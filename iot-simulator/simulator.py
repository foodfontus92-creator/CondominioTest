#!/usr/bin/env python3
"""AccesoLibre ESP32 simulator.

Emulates the physical controller against the local Mosquitto broker:
  * listens on   condo/{condoId}/device/{deviceId}/command
  * publishes on condo/{condoId}/device/{deviceId}/telemetry
  * validates compact QR strings pasted in the terminal 100% offline
    (same algorithm as firmware-esp32/src/qr_verifier.cpp and the backend
    CompactQrCryptoService), even while disconnected from the broker.

QR format (ARCHITECTURE.md section 5):
    PASS_ID|CONDO_ID|UNIT_ID|EXP_UNIX_TIMESTAMP|HMAC_SHA256_TRUNCATED
"""
import argparse
import base64
import collections
import hashlib
import hmac
import json
import os
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Optional

import paho.mqtt.client as mqtt

QR_MAX_LEN = 120           # payload length must be < 120
HMAC_TRUNC_BYTES = 12
MIN_VALID_UNIX = 1704067200
SCHEMA_VERSION = 1
FW_VERSION = "sim-1.0.0"
RELAY_PULSE_MS = 1000
COMMAND_MAX_AGE_SEC = 60
HEARTBEAT_INTERVAL_SEC = 30
B64URL_CHARS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
HEX_CHARS = set("0123456789abcdefABCDEF")


# --------------------------------------------------------------------------
# Crypto / QR protocol (pure functions, unit-tested in test_simulator.py)
# --------------------------------------------------------------------------
def uuid_to_b64(value: str) -> str:
    """UUID -> Base64URL without padding (22 chars), same as the backend."""
    return base64.urlsafe_b64encode(uuid.UUID(value).bytes).rstrip(b"=").decode("ascii")


def derive_unit_secret(condo_key: bytes, unit_id_b64: str) -> bytes:
    return hmac.new(condo_key, unit_id_b64.encode("utf-8"), hashlib.sha256).digest()


def compute_mac_hex(unit_secret: bytes, signing_input: str) -> str:
    digest = hmac.new(unit_secret, signing_input.encode("utf-8"), hashlib.sha256).digest()
    return digest[:HMAC_TRUNC_BYTES].hex()


def generate_qr(condo_key: bytes, condo_id: str, unit_id: str, exp_unix: int,
                pass_id: Optional[str] = None) -> str:
    pass_b64 = uuid_to_b64(pass_id or str(uuid.uuid4()))
    condo_b64 = uuid_to_b64(condo_id)
    unit_b64 = uuid_to_b64(unit_id)
    signing_input = f"{pass_b64}|{condo_b64}|{unit_b64}|{exp_unix}"
    mac = compute_mac_hex(derive_unit_secret(condo_key, unit_b64), signing_input)
    return f"{signing_input}|{mac}"


@dataclass
class QrCheck:
    result: str                 # backend AccessResult name
    parsed: bool = False
    pass_b64: str = ""
    unit_b64: str = ""
    exp_unix: int = 0
    detail: str = ""


def verify_qr(payload: str, now: int, condo_key: bytes, condo_id_b64: str,
              revoked: dict) -> QrCheck:
    """Mirror of verifyCompactQr() in the firmware. `now`=0 means no valid clock."""
    bad = QrCheck("DENIED_INVALID_HMAC", detail="malformed payload")
    if not payload or len(payload) >= QR_MAX_LEN:
        return bad
    tokens = payload.split("|")
    if len(tokens) != 5:
        return bad
    for t in tokens[:3]:
        if len(t) != 22 or any(c not in B64URL_CHARS for c in t):
            return bad
    if not (1 <= len(tokens[3]) <= 10) or not tokens[3].isascii() or not tokens[3].isdigit():
        return bad
    exp_unix = int(tokens[3])
    if exp_unix <= 0:
        return bad
    if len(tokens[4]) != HMAC_TRUNC_BYTES * 2 or any(c not in HEX_CHARS for c in tokens[4]):
        return bad

    out = QrCheck("", True, tokens[0], tokens[2], exp_unix)
    if tokens[1] != condo_id_b64:
        out.result, out.detail = "DENIED_WRONG_CONDO", "QR belongs to another condominium"
        return out
    if now < MIN_VALID_UNIX:
        out.result, out.detail = "ERROR", "no trustworthy clock (fail closed)"
        return out
    if now > exp_unix:
        out.result, out.detail = "DENIED_EXPIRED", f"expired {now - exp_unix}s ago"
        return out
    if tokens[0] in revoked:
        out.result, out.detail = "DENIED_REVOKED", "pass revoked via REVOKE_PASS"
        return out

    signing_input = "|".join(tokens[:4])
    expected = compute_mac_hex(derive_unit_secret(condo_key, tokens[2]), signing_input)
    if hmac.compare_digest(expected, tokens[4].lower()):
        out.result, out.detail = "GRANTED", "HMAC-SHA256 signature valid"
    else:
        out.result, out.detail = "DENIED_INVALID_HMAC", "signature mismatch"
    return out


def compact_json(obj) -> str:
    return json.dumps(obj, separators=(",", ":"))


# --------------------------------------------------------------------------
# Simulated device
# --------------------------------------------------------------------------
class SimulatedDevice:
    def __init__(self, args):
        self.condo_id = str(uuid.UUID(args.condo_id))
        self.device_id = str(uuid.UUID(args.device_id))
        self.condo_key = bytes.fromhex(args.condo_secret)
        if len(self.condo_key) != 32:
            raise SystemExit("condo secret must be 64 hex chars (32 bytes)")
        self.condo_id_b64 = uuid_to_b64(self.condo_id)
        self.key_version = args.key_version
        self.gate_timeout = args.gate_timeout
        self.heartbeat_interval = args.heartbeat
        self.honor_pulse = args.honor_command_pulse

        self.revoked = {}
        self.seen_commands = collections.deque(maxlen=8)
        self.pending = collections.deque(maxlen=32)   # offline telemetry queue
        self.lock = threading.RLock()
        self.connected = False
        self.stop = threading.Event()

        self.gate_open_since: Optional[float] = None
        self.gate_alerted = False
        self.relay_off_at = 0.0

        self.topic_cmd = f"condo/{self.condo_id}/device/{self.device_id}/command"
        self.topic_tel = f"condo/{self.condo_id}/device/{self.device_id}/telemetry"

        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                                  client_id=f"sim-{self.device_id}")
        self.client.username_pw_set(args.user, args.password)
        # LWT identical to ARCHITECTURE.md 6.1
        self.client.will_set(self.topic_tel,
                             compact_json({"event": "device_offline",
                                           "schema_version": SCHEMA_VERSION,
                                           "status": "OFFLINE"}),
                             qos=1, retain=True)
        self.client.reconnect_delay_set(min_delay=1, max_delay=30)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message
        self.host, self.port = args.host, args.port

    # ---- logging ---------------------------------------------------------
    @staticmethod
    def log(tag: str, msg: str):
        print(f"[{time.strftime('%H:%M:%S')}] [{tag}] {msg}", flush=True)

    # ---- lifecycle -------------------------------------------------------
    def start(self):
        self.log("sim", f"device={self.device_id}")
        self.log("sim", f"condo ={self.condo_id} (b64 {self.condo_id_b64})")
        self.log("sim", f"cmd topic: {self.topic_cmd}")
        self.log("sim", f"tel topic: {self.topic_tel}")
        self.client.connect_async(self.host, self.port, keepalive=30)
        self.client.loop_start()
        threading.Thread(target=self._background, daemon=True).start()

    def shutdown(self):
        self.stop.set()
        self.client.loop_stop()
        self.client.disconnect()

    # ---- MQTT callbacks --------------------------------------------------
    def _on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            self.log("mqtt", f"connection refused: {reason_code}")
            return
        self.connected = True
        self.log("mqtt", f"connected to {self.host}:{self.port}")
        client.subscribe(self.topic_cmd, qos=1)
        # retained heartbeat overwrites a retained LWT from a previous session
        self._publish(self._heartbeat(), retain=True, queue_if_offline=False)
        self._flush_pending()

    def _on_disconnect(self, client, userdata, flags, reason_code, properties):
        self.connected = False
        self.log("mqtt", f"disconnected ({reason_code}); QR validation keeps working offline")

    def _on_message(self, client, userdata, msg):
        try:
            self.handle_command(json.loads(msg.payload.decode("utf-8")))
        except (ValueError, UnicodeDecodeError) as exc:
            self.log("cmd", f"invalid JSON ignored: {exc}")

    # ---- publishing ------------------------------------------------------
    def _envelope(self, event: str, payload: dict, occurred_at: Optional[int] = None) -> str:
        doc = {"event": event, "schema_version": SCHEMA_VERSION}
        ts = int(time.time()) if occurred_at is None else occurred_at
        if ts > 0:
            doc["occurred_at"] = ts
        doc["payload"] = payload
        return compact_json(doc)

    def _heartbeat(self) -> str:
        return self._envelope("HEARTBEAT", {
            "status": "ONLINE",
            "firmware_version": FW_VERSION,
            "ip_address": "127.0.0.1",
            "wifi_rssi_dbm": -50,
            "uptime_seconds": int(time.monotonic()),
            "free_heap_bytes": 200000,
            "hmac_key_version_synced": self.key_version,
            "door_sensor_open": self.gate_open_since is not None,
        })

    def _publish(self, message: str, retain=False, queue_if_offline=True) -> bool:
        with self.lock:
            if self.connected:
                info = self.client.publish(self.topic_tel, message, qos=1, retain=retain)
                return info.rc == mqtt.MQTT_ERR_SUCCESS
            if queue_if_offline:
                self.pending.append(message)
                self.log("net", f"offline: telemetry queued ({len(self.pending)} pending)")
            return False

    def _flush_pending(self):
        with self.lock:
            while self.pending and self.connected:
                msg = self.pending.popleft()
                self.client.publish(self.topic_tel, msg, qos=1)
                self.log("net", "queued telemetry sent")

    def _ack(self, command_id: str, status: str, error: Optional[str] = None):
        self._publish(self._envelope("COMMAND_ACK", {
            "command_id": command_id, "status": status, "error_message": error}))

    # ---- commands (backend -> device) ----------------------------------------
    def handle_command(self, doc: dict):
        command_id = doc.get("command_id") or ""
        ctype = doc.get("type") or ""
        issued_at = int(doc.get("issued_at") or 0)
        payload = doc.get("payload") or {}
        if not command_id or not ctype:
            self.log("cmd", "missing command_id/type, ignored")
            return
        if command_id in self.seen_commands:
            self.log("cmd", f"duplicate {command_id} ignored")
            self._ack(command_id, "EXECUTED", "duplicate_ignored")
            return
        self.seen_commands.append(command_id)

        if ctype == "UNLOCK":
            if issued_at and abs(time.time() - issued_at) > COMMAND_MAX_AGE_SEC:
                self.log("cmd", "UNLOCK rejected: stale command")
                self._ack(command_id, "REJECTED", "stale_command")
                return
            pulse = RELAY_PULSE_MS
            if self.honor_pulse:
                pulse = max(200, min(30000, int(payload.get("pulse_ms") or RELAY_PULSE_MS)))
            print("\n" + "=" * 62)
            print(f" >>> OPEN COMMAND RECEIVED FROM BACKEND  (command_id={command_id})")
            print(f"     reason={payload.get('reason')}  user={payload.get('triggered_by_user_id')}")
            print(f"     unit={payload.get('unit_id')}  intercom_call={payload.get('intercom_call_id')}")
            print("=" * 62)
            self.relay_pulse(pulse)
            self._ack(command_id, "EXECUTED")
        elif ctype == "SYNC_CONFIG":
            if payload.get("condo_id_b64") != self.condo_id_b64:
                self._ack(command_id, "REJECTED", "condo_id_mismatch")
                return
            try:
                key = bytes.fromhex(payload.get("hmac_secret_hex", ""))
                if len(key) != 32:
                    raise ValueError
            except ValueError:
                self._ack(command_id, "REJECTED", "invalid_hmac_secret_hex")
                return
            self.condo_key = key
            self.key_version = int(payload.get("hmac_key_version") or 1)
            self.log("cmd", f"SYNC_CONFIG applied, key version {self.key_version}")
            self._ack(command_id, "EXECUTED")
        elif ctype == "REVOKE_PASS":
            pass_b64 = payload.get("pass_id_b64", "")
            if len(pass_b64) != 22:
                self._ack(command_id, "REJECTED", "invalid_pass_id")
                return
            self.revoked[pass_b64] = int(payload.get("expires_at") or 0)
            self.log("cmd", f"REVOKE_PASS {pass_b64}")
            self._ack(command_id, "EXECUTED")
        elif ctype == "INTERCOM_ACTION":
            self.log("cmd", f"INTERCOM_ACTION {payload.get('action')} call={payload.get('call_id')}")
            self._ack(command_id, "EXECUTED")
        else:
            self.log("cmd", f"unsupported type {ctype}")
            self._ack(command_id, "REJECTED", "unsupported_type")

    # ---- relay & gate -------------------------------------------------------
    def relay_pulse(self, ms: int):
        with self.lock:
            self.relay_off_at = max(self.relay_off_at, time.monotonic() + ms / 1000.0)
        self.log("relay", f"ON for {ms} ms")

    def open_gate(self):
        with self.lock:
            if self.gate_open_since is None:
                self.gate_open_since = time.monotonic()
                self.gate_alerted = False
        self.log("gate", f"OPEN (alert after {self.gate_timeout}s)")

    def close_gate(self):
        with self.lock:
            if self.gate_open_since is None:
                return
            open_sec = int(time.monotonic() - self.gate_open_since)
            alerted = self.gate_alerted
            self.gate_open_since, self.gate_alerted = None, False
        self.log("gate", f"CLOSED after {open_sec}s")
        if alerted:
            self._publish(self._envelope("GATE_CLOSED", {
                "open_seconds": open_sec, "threshold_seconds": self.gate_timeout}))

    def _background(self):
        next_hb = time.monotonic() + self.heartbeat_interval
        relay_on = False
        while not self.stop.wait(0.2):
            now = time.monotonic()
            with self.lock:
                active = now < self.relay_off_at
                if relay_on and not active:
                    self.log("relay", "OFF")
                relay_on = active
                alert_sec = None
                if (self.gate_open_since is not None and not self.gate_alerted
                        and now - self.gate_open_since >= self.gate_timeout):
                    self.gate_alerted = True
                    alert_sec = int(now - self.gate_open_since)
            if alert_sec is not None:
                self.log("gate", f"ALERT: gate open for {alert_sec}s -> telemetry")
                self._publish(self._envelope("GATE_OPEN_TIMEOUT", {
                    "open_seconds": alert_sec, "threshold_seconds": self.gate_timeout}))
            if now >= next_hb:
                next_hb = now + self.heartbeat_interval
                if self.connected:
                    self._publish(self._heartbeat(), queue_if_offline=False)

    # ---- QR scanning (keyboard = physical GM65 scan) ---------------------------
    def scan(self, payload: str):
        t0 = time.perf_counter()
        check = verify_qr(payload.strip(), int(time.time()), self.condo_key,
                          self.condo_id_b64, self.revoked)
        if check.result == "GRANTED":
            self.relay_pulse(RELAY_PULSE_MS)
        latency = int((time.perf_counter() - t0) * 1000)
        mark = "OPEN" if check.result == "GRANTED" else "DENIED"
        self.log("qr", f"{mark}: {check.result} - {check.detail} "
                       f"(pass={check.pass_b64 or '-'}, {latency} ms, "
                       f"{'online' if self.connected else 'OFFLINE'})")
        event = {
            "access_method": "QR_OFFLINE",
            "result": check.result,
            "offline_validated": True,
        }
        if check.parsed:
            event["pass_id_b64"] = check.pass_b64
            event["unit_id_b64"] = check.unit_b64
        event["latency_ms"] = latency
        event["command_id"] = None
        self._publish(self._envelope("ACCESS_EVENT", event))

    def status(self):
        print(f"  mqtt         : {'connected' if self.connected else 'DISCONNECTED'}")
        print(f"  key version  : {self.key_version}")
        print(f"  gate         : {'OPEN' if self.gate_open_since is not None else 'closed'}")
        print(f"  revoked ids  : {len(self.revoked)}")
        print(f"  queued events: {len(self.pending)}")


HELP = """
Paste a QR string (PASS_ID|CONDO_ID|UNIT_ID|EXP|HMAC) and press Enter to simulate a scan.
Commands:
  /gen <unit_uuid> [ttl_seconds]   generate a signed test QR (default ttl 3600)
  /open                            reed switch: gate opens
  /close                           reed switch: gate closes
  /disconnect | /connect           simulate server outage (QR keeps working)
  /status                          show device state
  /help | /quit
"""


def repl(device: SimulatedDevice):
    print(HELP)
    while True:
        try:
            line = input("qr> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if not line:
            continue
        if not line.startswith("/"):
            device.scan(line)
            continue
        parts = line.split()
        cmd = parts[0].lower()
        if cmd in ("/quit", "/exit"):
            return
        elif cmd == "/help":
            print(HELP)
        elif cmd == "/open":
            device.open_gate()
        elif cmd == "/close":
            device.close_gate()
        elif cmd == "/status":
            device.status()
        elif cmd == "/disconnect":
            device.client.disconnect()
        elif cmd == "/connect":
            try:
                device.client.reconnect()
            except OSError as exc:
                device.log("mqtt", f"reconnect failed: {exc}")
        elif cmd == "/gen" and len(parts) >= 2:
            try:
                ttl = int(parts[2]) if len(parts) > 2 else 3600
                qr = generate_qr(device.condo_key, device.condo_id, parts[1], int(time.time()) + ttl)
                print(qr)
                print(f"({len(qr)} chars)")
            except ValueError as exc:
                print(f"invalid arguments: {exc}")
        else:
            print("unknown command, try /help")


def parse_args(argv=None):
    env = os.environ.get
    p = argparse.ArgumentParser(description="AccesoLibre ESP32 simulator")
    p.add_argument("--host", default=env("SIM_MQTT_HOST", "localhost"))
    p.add_argument("--port", type=int, default=int(env("SIM_MQTT_PORT", "1883")))
    p.add_argument("--user", default=env("SIM_MQTT_USER", "accesolibre_device"))
    p.add_argument("--password", default=env("SIM_MQTT_PASSWORD", "mqtt_device_local_dev"))
    p.add_argument("--condo-id", default=env("SIM_CONDO_ID"), required=env("SIM_CONDO_ID") is None,
                   help="condominiums.id (UUID)")
    p.add_argument("--device-id", default=env("SIM_DEVICE_ID"), required=env("SIM_DEVICE_ID") is None,
                   help="iot_devices.id (UUID)")
    p.add_argument("--condo-secret", default=env("SIM_CONDO_SECRET"),
                   required=env("SIM_CONDO_SECRET") is None,
                   help="condominiums.hmac_secret (64 hex chars)")
    p.add_argument("--key-version", type=int, default=1)
    p.add_argument("--gate-timeout", type=int, default=45,
                   help="seconds the gate may stay open before alert (default 45)")
    p.add_argument("--heartbeat", type=int, default=HEARTBEAT_INTERVAL_SEC)
    p.add_argument("--honor-command-pulse", action="store_true",
                   help="use pulse_ms from UNLOCK instead of fixed 1000 ms")
    return p.parse_args(argv)


def main():
    device = SimulatedDevice(parse_args())
    device.start()
    try:
        repl(device)
    finally:
        device.shutdown()


if __name__ == "__main__":
    sys.exit(main())
