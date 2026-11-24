"""Known-answer tests shared with backend CompactQrCryptoServiceTest (Java).

Run: python -m unittest test_simulator -v
"""
import time
import unittest

import simulator as sim

CONDO_SECRET = bytes.fromhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
CONDO_ID = "11111111-2222-3333-4444-555555555555"
UNIT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
PASS_ID = "01234567-89ab-cdef-0123-456789abcdef"
EXP = 1795458600
# Same constants are asserted by the Java backend test.
KAT_QR = ("ASNFZ4mrze8BI0VniavN7w|ERERESIiMzNERFVVVVVVVQ|qqqqqru7zMzd3e7u7u7u7g|"
          "1795458600|126be26948f327d28a1c2d37")
KAT_UNIT_SECRET = "70ff41ec102789ed76ad4e4613b9169f8ccb6b46d98526f4d3103f16fbf07429"


class QrProtocolTest(unittest.TestCase):
    def setUp(self):
        self.condo_b64 = sim.uuid_to_b64(CONDO_ID)

    def test_known_answer_matches_backend(self):
        unit_b64 = sim.uuid_to_b64(UNIT_ID)
        self.assertEqual(sim.derive_unit_secret(CONDO_SECRET, unit_b64).hex(), KAT_UNIT_SECRET)
        qr = sim.generate_qr(CONDO_SECRET, CONDO_ID, UNIT_ID, EXP, PASS_ID)
        self.assertEqual(qr, KAT_QR)
        self.assertEqual(len(qr), 104)

    def check(self, qr, now=EXP - 10, revoked=None):
        return sim.verify_qr(qr, now, CONDO_SECRET, self.condo_b64, revoked or {})

    def test_granted(self):
        self.assertEqual(self.check(KAT_QR).result, "GRANTED")

    def test_expired(self):
        self.assertEqual(self.check(KAT_QR, now=EXP + 1).result, "DENIED_EXPIRED")
        self.assertEqual(self.check(KAT_QR, now=EXP).result, "GRANTED")  # now <= exp

    def test_tampered_signature_and_unit(self):
        bad_mac = KAT_QR[:-1] + ("0" if KAT_QR[-1] != "0" else "1")
        self.assertEqual(self.check(bad_mac).result, "DENIED_INVALID_HMAC")
        other_unit = KAT_QR.replace("qqqqqru7zMzd3e7u7u7u7g", "qqqqqru7zMzd3e7u7u7u7h")
        self.assertEqual(self.check(other_unit).result, "DENIED_INVALID_HMAC")

    def test_wrong_condo(self):
        other = sim.generate_qr(CONDO_SECRET, "99999999-2222-3333-4444-555555555555", UNIT_ID, EXP)
        self.assertEqual(self.check(other).result, "DENIED_WRONG_CONDO")

    def test_wrong_key(self):
        qr = sim.generate_qr(bytes(32), CONDO_ID, UNIT_ID, EXP)
        self.assertEqual(self.check(qr).result, "DENIED_INVALID_HMAC")

    def test_revoked(self):
        self.assertEqual(self.check(KAT_QR, revoked={"ASNFZ4mrze8BI0VniavN7w": EXP}).result, "DENIED_REVOKED")

    def test_malformed_and_jwt(self):
        self.assertEqual(self.check("").result, "DENIED_INVALID_HMAC")
        self.assertEqual(self.check("a|b|c").result, "DENIED_INVALID_HMAC")
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln"
        self.assertEqual(self.check(jwt).result, "DENIED_INVALID_HMAC")
        self.assertEqual(self.check(KAT_QR + "x" * 20).result, "DENIED_INVALID_HMAC")  # >= 120 chars

    def test_no_clock_fails_closed(self):
        self.assertEqual(self.check(KAT_QR, now=0).result, "ERROR")

    def test_uppercase_mac_accepted_like_backend(self):
        head, mac = KAT_QR.rsplit("|", 1)
        self.assertEqual(self.check(f"{head}|{mac.upper()}").result, "GRANTED")


class CommandHandlingTest(unittest.TestCase):
    def make_device(self):
        args = sim.parse_args(["--condo-id", CONDO_ID,
                               "--device-id", "22222222-3333-4444-5555-666666666666",
                               "--condo-secret", CONDO_SECRET.hex()])
        device = sim.SimulatedDevice(args)
        device.sent = []
        device._publish = lambda msg, retain=False, queue_if_offline=True: device.sent.append(msg) or True
        return device

    def test_topics_match_architecture(self):
        d = self.make_device()
        self.assertEqual(d.topic_cmd, f"condo/{CONDO_ID}/device/22222222-3333-4444-5555-666666666666/command")
        self.assertTrue(d.topic_tel.endswith("/telemetry"))

    def test_unlock_executes_and_acks(self):
        d = self.make_device()
        d.handle_command({"command_id": "c1", "type": "UNLOCK", "issued_at": int(time.time()),
                          "payload": {"pulse_ms": 3000, "reason": "REMOTE_APP"}})
        self.assertGreater(d.relay_off_at, time.monotonic())
        self.assertIn('"event":"COMMAND_ACK"', d.sent[-1])
        self.assertIn('"status":"EXECUTED"', d.sent[-1])

    def test_stale_unlock_rejected(self):
        d = self.make_device()
        d.handle_command({"command_id": "c2", "type": "UNLOCK", "issued_at": int(time.time()) - 600,
                          "payload": {}})
        self.assertIn('"status":"REJECTED"', d.sent[-1])
        self.assertEqual(d.relay_off_at, 0.0)

    def test_revoke_then_scan_denied(self):
        d = self.make_device()
        d.handle_command({"command_id": "c3", "type": "REVOKE_PASS", "issued_at": int(time.time()),
                          "payload": {"pass_id_b64": "ASNFZ4mrze8BI0VniavN7w", "expires_at": EXP}})
        d.sent.clear()
        qr = sim.generate_qr(CONDO_SECRET, CONDO_ID, UNIT_ID, int(time.time()) + 600, PASS_ID)
        d.scan(qr)
        self.assertIn('"result":"DENIED_REVOKED"', d.sent[-1])

    def test_sync_config_rotates_key(self):
        d = self.make_device()
        new_key = bytes(range(32, 64))
        d.handle_command({"command_id": "c4", "type": "SYNC_CONFIG", "issued_at": int(time.time()),
                          "payload": {"condo_id_b64": d.condo_id_b64, "hmac_secret_hex": new_key.hex(),
                                      "hmac_key_version": 2}})
        self.assertEqual(d.key_version, 2)
        qr = sim.generate_qr(new_key, CONDO_ID, UNIT_ID, int(time.time()) + 600)
        d.scan(qr)
        self.assertIn('"result":"GRANTED"', d.sent[-1])

    def test_duplicate_command_not_reexecuted(self):
        d = self.make_device()
        cmd = {"command_id": "c5", "type": "UNLOCK", "issued_at": int(time.time()), "payload": {}}
        d.handle_command(cmd)
        d.relay_off_at = 0.0
        d.handle_command(cmd)
        self.assertEqual(d.relay_off_at, 0.0)


if __name__ == "__main__":
    unittest.main()
