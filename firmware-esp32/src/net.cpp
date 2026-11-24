#include "net.h"
#include <WiFi.h>
#include <PubSubClient.h>
#include "config.h"

namespace {
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);

char topicCommand[96];
char topicTelemetry[96];
char clientId[64];
Net::MessageHandler handler = nullptr;

uint32_t lastWifiAttemptMs = 0;
uint32_t nextMqttAttemptMs = 0;
uint32_t mqttBackoffMs = MQTT_RETRY_MIN_MS;
bool wasWifiUp = false;
bool justConnected = false;

const char LWT_PAYLOAD[] = "{\"event\":\"device_offline\",\"schema_version\":1,\"status\":\"OFFLINE\"}";

void onMessage(char* topic, uint8_t* payload, unsigned int length) {
  if (handler != nullptr && strcmp(topic, topicCommand) == 0) {
    handler(reinterpret_cast<const char*>(payload), length);
  }
}

void startWifi() {
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiAttemptMs = millis();
}

void tryMqttConnect() {
  Serial.printf("[mqtt] connecting to %s:%d ...\n", MQTT_HOST, MQTT_PORT);
  // LWT: qos 1, retain true, on the telemetry topic (ARCHITECTURE.md 6.1)
  const bool ok = mqtt.connect(clientId, MQTT_USER, MQTT_PASSWORD, topicTelemetry, 1, true, LWT_PAYLOAD);
  if (ok) {
    mqtt.subscribe(topicCommand, 1);
    mqttBackoffMs = MQTT_RETRY_MIN_MS;
    justConnected = true;
    Serial.printf("[mqtt] connected, subscribed to %s\n", topicCommand);
  } else {
    Serial.printf("[mqtt] failed rc=%d, retry in %lu ms\n", mqtt.state(), (unsigned long)mqttBackoffMs);
    nextMqttAttemptMs = millis() + mqttBackoffMs;
    mqttBackoffMs = min<uint32_t>(mqttBackoffMs * 2, MQTT_RETRY_MAX_MS);
  }
}
}  // namespace

namespace Net {

void begin(MessageHandler onCommand) {
  handler = onCommand;
  snprintf(topicCommand, sizeof(topicCommand), "condo/%s/device/%s/command", CONDO_ID, DEVICE_ID);
  snprintf(topicTelemetry, sizeof(topicTelemetry), "condo/%s/device/%s/telemetry", CONDO_ID, DEVICE_ID);
  snprintf(clientId, sizeof(clientId), "esp32-%s", DEVICE_ID);

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(MQTT_BUFFER_BYTES);
  mqtt.setKeepAlive(30);
  mqtt.setSocketTimeout(3);
  mqtt.setCallback(onMessage);

  WiFi.mode(WIFI_STA);
  WiFi.persistent(false);
  WiFi.setAutoReconnect(true);
  startWifi();
}

void loop() {
  const uint32_t now = millis();

  if (WiFi.status() != WL_CONNECTED) {
    if (wasWifiUp) {
      wasWifiUp = false;
      Serial.println(F("[wifi] link lost"));
    }
    if ((uint32_t)(now - lastWifiAttemptMs) >= WIFI_RETRY_MS) {
      Serial.println(F("[wifi] (re)connecting..."));
      startWifi();
    }
    return;
  }
  if (!wasWifiUp) {
    wasWifiUp = true;
    nextMqttAttemptMs = 0;
    Serial.printf("[wifi] connected, ip=%s\n", WiFi.localIP().toString().c_str());
  }

  if (!mqtt.connected()) {
    if ((int32_t)(now - nextMqttAttemptMs) >= 0) tryMqttConnect();
    return;
  }
  mqtt.loop();
}

bool connected() { return WiFi.status() == WL_CONNECTED && mqtt.connected(); }

bool consumeJustConnected() {
  const bool v = justConnected;
  justConnected = false;
  return v;
}

bool publishTelemetry(const char* json, bool retain) {
  if (!connected()) return false;
  return mqtt.publish(topicTelemetry, json, retain);
}

const char* commandTopic() { return topicCommand; }
const char* telemetryTopic() { return topicTelemetry; }

}  // namespace Net
