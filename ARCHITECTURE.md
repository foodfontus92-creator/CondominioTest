# AccesoLibre — Arquitectura de Plataforma y Contratos de Ingeniería

> **Versión:** 1.0 (Fase 1 — Infraestructura, Esquema y Contratos Base)  
> **Modelo de Despliegue:** 100% On-Premise / Red Local (LAN) / Offline-First  
> **Naturaleza:** Plataforma SaaS Multi-Tenant de Control de Acceso Residencial Autónomo (Sin Conserjería)

---

## 1. Principios y Reglas Estrictas de Ingeniería (Obligatorias)

1. **Arquitectura 100% Local y Offline-First (Cero Dependencias de Nube Paga):**
   - Todo el stack central (**PostgreSQL 16**, **Eclipse Mosquitto MQTT**, **Backend Spring Boot**, **Web Admin Nginx**) corre localmente en contenedores Docker dentro de la red interna `accesolibre-net`.
   - Prohibido depender de servicios cloud externos pagos (AWS, GCP, Firebase, Twilio, Auth0, brokers MQTT públicos, etc.) para autenticación, mensajería, señalización de intercomunicador o validación de accesos.
   - Los controladores físicos (**ESP32**) validan pases QR **de forma 100% autónoma y offline** utilizando el secreto HMAC sincronizado del condominio y su reloj local (RTC/NTP en LAN), incluso si el servidor local o el router Wi-Fi pierden conectividad momentánea.

2. **Aislamiento Multi-Tenant Estricto:**
   - El límite de inquilino (*tenant boundary*) es `condominiums.id` (`UUID`).
   - Toda entidad operativa (`units`, `users`, `iot_devices`, `access_passes`, `access_logs`, `intercom_calls`) incluye obligatoriamente `condominium_id` y restricciones de clave foránea compuestas a nivel de PostgreSQL para impedir cruces de datos entre condominios.
   - Todo topic MQTT y todo endpoint REST (excepto autenticación inicial y administración global `SUPER_ADMIN`) está particionado por `condoId`.

3. **Protocolo de QR Compacto Offline (Prohibido JWT en QR):**
   - Los códigos QR generados por la plataforma **NO pueden contener JWT**.
   - Deben utilizar estrictamente el formato compacto separado por pipes (`|`) con longitud total **menor a 120 caracteres** (exactamente **104 caracteres** en la especificación canónica).

4. **Cero Mock Data:**
   - Prohibido devolver datos simulados, colecciones en memoria o respuestas *hardcoded* en producción o desarrollo del backend/frontend.
   - Toda información debe persistirse y consultarse desde PostgreSQL mediante repositorios reales, migraciones Flyway versionadas y DTOs tipados.

5. **Persistencia de Contexto:**
   - Este archivo (`ARCHITECTURE.md`) es la fuente única de verdad (*Single Source of Truth*) para todas las fases del monorepo. Cualquier cambio de protocolo, topic, puerto o contrato de datos debe reflejarse aquí primero.

---

## 2. Estructura del Monorepo

```text
accesolibre/
├── ARCHITECTURE.md                  # Especificación técnica, reglas y contratos (este documento)
├── docker-compose.yml               # Orquestación local del stack completo en accesolibre-net
├── backend/                         # API REST, WebSocket (intercom), integración MQTT y Flyway (Java 21 + Spring Boot 3)
│   ├── Dockerfile                   # Build multi-stage (Maven JDK 21 -> JRE 21 Alpine)
│   └── src/main/resources/db/migration/
│       └── V1__init_schema.sql      # Migración inicial del esquema multi-tenant
├── web-admin/                       # Panel administrativo multi-tenant (React + TypeScript + Vite -> Nginx)
│   └── Dockerfile                   # Build multi-stage (Node 22 Alpine -> Nginx 1.27 Alpine)
├── mosquitto/
│   └── config/
│       └── mosquitto.conf           # Configuración del broker MQTT con autenticación obligatoria
├── firmware-esp32/                  # Firmware C++ (ESP-IDF / PlatformIO) para lectores QR, relés e intercom
├── iot-simulator/                   # Simulador Python/CLI de dispositivos ESP32 contra Mosquitto local
└── mobile-android/                  # App nativa Android (Kotlin) para residentes: generación de QR y atención de intercom
```

---

## 3. Topología de Red Local y Puertos

Todos los contenedores operan dentro de la red Docker bridge `accesolibre-net`.

| Servicio Docker | Imagen Base | Puerto Interno | Puerto Expuesto (Host LAN) | Protocolo | Propósito |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `postgres` | `postgres:16-alpine` | `5432` | `127.0.0.1:5432` *(configurable)* | TCP (PostgreSQL) | Persistencia relacional transaccional multi-tenant |
| `mosquitto` | `eclipse-mosquitto:2.0` | `1883` / `9001` | `0.0.0.0:1883` / `0.0.0.0:9001` | MQTT / MQTT over WS | Bus de comandos y telemetría IoT en red local |
| `backend` | `eclipse-temurin:21-jre-alpine` | `8080` | `0.0.0.0:8080` | HTTP/REST + WS | Lógica de negocio, Auth, emisión QR, bridge MQTT y señalización intercom |
| `web-admin` | `nginx:1.27-alpine` | `80` | `0.0.0.0:80` *(o `WEB_ADMIN_PORT`)* | HTTP (Reverse Proxy) | SPA Web Admin + proxy reverso `/api/` y `/ws/` hacia `backend:8080` |

### Variables de Entorno Clave (Docker Compose)

| Variable | Valor por Defecto Local | Descripción |
| :--- | :--- | :--- |
| `POSTGRES_DB` | `accesolibre` | Nombre de la base de datos |
| `POSTGRES_USER` | `accesolibre` | Usuario de aplicación PostgreSQL |
| `POSTGRES_PASSWORD` | `accesolibre_local_dev` | Contraseña local *(cambiar en despliegue real)* |
| `MQTT_BACKEND_USER` | `accesolibre_backend` | Usuario MQTT usado por el servicio Spring Boot |
| `MQTT_BACKEND_PASSWORD` | `mqtt_backend_local_dev` | Contraseña del backend en Mosquitto |
| `MQTT_DEVICE_USER` | `accesolibre_device` | Usuario MQTT inicial para simulador/provisión ESP32 |
| `MQTT_DEVICE_PASSWORD` | `mqtt_device_local_dev` | Contraseña de dispositivos IoT en Mosquitto |
| `JWT_SECRET` | *(mín. 32 bytes hex)* | Secreto exclusivo para sesiones HTTP de usuarios (Web/Android). **Nunca usar en QR.** |
| `WEB_ADMIN_PORT` | `80` | Puerto HTTP expuesto en el host LAN para Web Admin |

---

## 4. Convenciones de Nombres

| Capa / Artefacto | Convención | Ejemplo |
| :--- | :--- | :--- |
| **PostgreSQL** (tablas, columnas, índices, constraints) | `snake_case` plural en tablas, prefijos `idx_`, `uq_`, `fk_`, `ck_` | `access_passes`, `condominium_id`, `idx_access_logs_condo_occurred` |
| **Migraciones Flyway** | `V<n>__<descripcion_snake_case>.sql` | `V1__init_schema.sql` |
| **Java / Spring Boot** (clases, paquetes, métodos) | `PascalCase` clases, `camelCase` métodos/campos, paquete raíz `com.accesolibre` | `AccessPassService`, `condominiumId` |
| **API REST** (rutas y payloads JSON) | Rutas en `kebab-case` bajo `/api/v1`, JSON en `camelCase`, fechas en ISO-8601 UTC | `POST /api/v1/condos/{condoId}/access-passes` |
| **MQTT Topics** | Jerarquía fija en minúsculas con UUIDs canónicos | `condo/{condoId}/device/{deviceId}/command` |
| **MQTT Payloads JSON** | `snake_case` compacto (optimizado para `ArduinoJson` en ESP32) | `{"cmd":"unlock","pulse_ms":3000}` |
| **Frontend Web / Android** | `PascalCase` componentes, `camelCase` variables/funciones | `AccessPassList.tsx`, `IntercomCallViewModel.kt` |
| **Firmware ESP32** | C++ `snake_case` funciones/variables, `UPPER_SNAKE_CASE` constantes | `verify_offline_qr()`, `MAX_QR_PAYLOAD_LEN` |

---

## 5. Protocolo de QR Compacto Offline

### 5.1 Formato Canónico

```text
PASS_ID|CONDO_ID|UNIT_ID|EXP_UNIX_TIMESTAMP|HMAC_SHA256_TRUNCATED
```

- **Delimitador:** Pipe ASCII (`|`, `0x7C`). Exactamente 5 segmentos (4 separadores).
- **Límite estricto de longitud:** `< 120` caracteres ASCII (longitud canónica fija: **104 caracteres**).
- **Sin JWT:** Prohibido incluir cabeceras Base64 JSON, algoritmos o claims tipo JWT.

### 5.2 Especificación de Campos y Presupuesto de Longitud

| # | Campo | Codificación | Longitud | Descripción |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `PASS_ID` | UUID (16 bytes) en Base64URL sin padding (`RFC 4648 §5`) | `22` | Corresponde 1-a-1 con `access_passes.id` |
| 2 | `CONDO_ID` | UUID (16 bytes) en Base64URL sin padding (`RFC 4648 §5`) | `22` | Corresponde 1-a-1 con `condominiums.id` |
| 3 | `UNIT_ID` | UUID (16 bytes) en Base64URL sin padding (`RFC 4648 §5`) | `22` | Corresponde 1-a-1 con `units.id` |
| 4 | `EXP_UNIX_TIMESTAMP` | Entero decimal UTC (segundos desde Epoch) | `10` | Fecha de expiración (`access_passes.expires_at`), válido hasta el año 2286 |
| 5 | `HMAC_SHA256_TRUNCATED` | Primeros 12 bytes (96 bits) de `HMAC-SHA256` en hexadecimal minúscula | `24` | Firma criptográfica sobre el prefijo `PASS_ID\|CONDO_ID\|UNIT_ID\|EXP_UNIX_TIMESTAMP` |
| — | Separadores `\|` | 4 caracteres ASCII | `4` | — |
| **Total** | | | **`104`** | **< 120 caracteres (margen de 16 caracteres; cabe en QR Versión 5-6 con lectura rápida en ESP32)** |

### 5.3 Algoritmo de Firma, Derivación `unit_secret` y Verificación Offline (ESP32)

1. **Mensaje firmado (`signing_input`):**
   ```text
   <PASS_ID>|<CONDO_ID>|<UNIT_ID>|<EXP_UNIX_TIMESTAMP>
   ```
2. **Derivación criptográfica por unidad (`unit_secret`):**
   - Clave maestra del condominio: `condominiums.hmac_secret` (32 bytes / 64 caracteres hexadecimales), provista y almacenada en NVS del ESP32.
   - Clave derivada por unidad (`units.unit_secret`), entregada al residente autenticado mediante `GET /api/v1/access-passes/unit-secret` (el residente nunca recibe la clave maestra del condominio):
     ```text
     unit_secret_bytes = HMAC_SHA256(key = hex_decode(condo_hmac_secret), message = UTF8(UNIT_ID))
     unit_secret_hex   = hex_lowercase(unit_secret_bytes)  // 32 bytes = 64 chars hex
     ```
3. **Cálculo de firma del QR (Backend o App Android offline con `unit_secret`):**
   ```text
   digest = HMAC_SHA256(key = hex_decode(unit_secret_hex), message = UTF8(signing_input))
   HMAC_SHA256_TRUNCATED = hex_lowercase(digest[0..11])   // 12 bytes = 24 chars hex (96 bits)
   ```
4. **Flujo de validación en ESP32 (100% Offline, sin tabla de unidades ni decodificación Base64):**
   - Rechazar inmediatamente si `len(payload) >= 120` o si no contiene exactamente 5 tokens separados por `|`.
   - Verificar que `token[1]` coincide exactamente con el `CONDO_ID` (Base64URL de 22 chars) configurado en el ESP32 (`DENIED_WRONG_CONDO`).
   - Parsear `token[3]` como `int64_t` y validar `rtc_unix_now <= exp_unix` (`DENIED_EXPIRED`).
   - Verificar que `token[0]` (`PASS_ID`) no figura en la lista local NVS de pases revocados (`DENIED_REVOKED`).
   - Derivar al vuelo `unit_secret_bytes = HMAC_SHA256(condo_hmac_secret, UTF8(token[2]))` con `mbedtls_md_hmac`.
   - Recalcular `HMAC_SHA256(unit_secret_bytes, UTF8(signing_input))` y comparar los primeros 12 bytes (24 caracteres hex) en **tiempo constante** (`DENIED_INVALID_HMAC`).
   - Si es válido: activar el relé durante `relay_pulse_ms`, registrar el evento en buffer local (flash/RAM) y sincronizarlo al topic `telemetry` apenas el broker MQTT esté disponible.

---

## 6. Contratos MQTT (Eclipse Mosquitto Local)

### 6.1 Jerarquía Canónica de Topics

Todos los identificadores `{condoId}` y `{deviceId}` corresponden a los UUID canónicos (formato estándar de 36 caracteres `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) de `condominiums.id` e `iot_devices.id`.

| Dirección | Topic Canónico | QoS | Retain | Propósito |
| :--- | :--- | :---: | :---: | :--- |
| Backend → ESP32 | `condo/{condoId}/device/{deviceId}/command` | `1` | `false` | Comandos de ejecución hacia el controlador físico |
| ESP32 → Backend | `condo/{condoId}/device/{deviceId}/telemetry` | `1` | `false` *(LWT: `true`)* | Heartbeats, eventos de acceso (online/offline), llamadas de intercom y estado |

> **Last Will and Testament (LWT):** Al conectarse a Mosquitto, cada ESP32 registra como LWT el topic `condo/{condoId}/device/{deviceId}/telemetry` con `qos=1`, `retain=true` y payload `{"event":"device_offline","schema_version":1,"status":"OFFLINE"}`.

### 6.2 Contratos de Mensajes: `.../command` (Backend → Dispositivo)

Todos los comandos incluyen `command_id` (UUID para idempotencia/ACK), `type`, `issued_at` (Unix epoch en segundos) y `payload`.

#### a) Apertura Remota de Relé (`UNLOCK`)
```json
{
  "command_id": "9f2c1b4a-8d7e-4b3a-9c1d-2e3f4a5b6c7d",
  "type": "UNLOCK",
  "issued_at": 1795458600,
  "payload": {
    "pulse_ms": 3000,
    "reason": "REMOTE_APP",
    "triggered_by_user_id": "4b12c3d4-0000-4000-8000-000000000001",
    "unit_id": "2a11b2c3-0000-4000-8000-000000000010",
    "intercom_call_id": null
  }
}
```

#### b) Sincronización de Secreto HMAC y Reloj (`SYNC_CONFIG`)
```json
{
  "command_id": "1a2b3c4d-8d7e-4b3a-9c1d-2e3f4a5b6c7d",
  "type": "SYNC_CONFIG",
  "issued_at": 1795458600,
  "payload": {
    "condo_id_b64": "3q2-7_8AAAAAAAAAAAAAAA",
    "hmac_secret_hex": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "hmac_key_version": 1,
    "server_unix_time": 1795458600,
    "relay_pulse_ms": 3000
  }
}
```

#### c) Revocación de Pase QR Offline (`REVOKE_PASS`)
```json
{
  "command_id": "7e6d5c4b-8d7e-4b3a-9c1d-2e3f4a5b6c7d",
  "type": "REVOKE_PASS",
  "issued_at": 1795458605,
  "payload": {
    "pass_id_b64": "AbCdEfGhIjKlMnOpQrStUv",
    "expires_at": 1795545000
  }
}
```

#### d) Control de Llamada de Intercomunicador (`INTERCOM_ACTION`)
```json
{
  "command_id": "5c4b3a29-8d7e-4b3a-9c1d-2e3f4a5b6c7d",
  "type": "INTERCOM_ACTION",
  "issued_at": 1795458610,
  "payload": {
    "call_id": "88776655-4433-4221-8110-aabbccddeeff",
    "action": "ANSWERED",
    "signaling_session_id": "lan-rtc-88776655"
  }
}
```

### 6.3 Contratos de Mensajes: `.../telemetry` (Dispositivo → Backend)

#### a) Heartbeat Periódico (`HEARTBEAT`, cada 30 s)
```json
{
  "event": "HEARTBEAT",
  "schema_version": 1,
  "occurred_at": 1795458630,
  "payload": {
    "status": "ONLINE",
    "firmware_version": "1.0.0",
    "ip_address": "192.168.1.42",
    "wifi_rssi_dbm": -58,
    "uptime_seconds": 86400,
    "free_heap_bytes": 184320,
    "hmac_key_version_synced": 1,
    "door_sensor_open": false
  }
}
```

#### b) Evento de Validación de Acceso (`ACCESS_EVENT`, online u offline sincronizado)
```json
{
  "event": "ACCESS_EVENT",
  "schema_version": 1,
  "occurred_at": 1795458642,
  "payload": {
    "access_method": "QR_OFFLINE",
    "result": "GRANTED",
    "offline_validated": true,
    "pass_id_b64": "AbCdEfGhIjKlMnOpQrStUv",
    "unit_id_b64": "WxYz0123456789_-AbCdEf",
    "latency_ms": 14,
    "command_id": null
  }
}
```

#### c) Inicio de Llamada desde Panel de Intercomunicador (`INTERCOM_RING`)
```json
{
  "event": "INTERCOM_RING",
  "schema_version": 1,
  "occurred_at": 1795458700,
  "payload": {
    "intercom_extension": "402",
    "signaling_session_id": "lan-rtc-88776655"
  }
}
```

#### d) Confirmación de Comando (`COMMAND_ACK`)
```json
{
  "event": "COMMAND_ACK",
  "schema_version": 1,
  "occurred_at": 1795458601,
  "payload": {
    "command_id": "9f2c1b4a-8d7e-4b3a-9c1d-2e3f4a5b6c7d",
    "status": "EXECUTED",
    "error_message": null
  }
}
```

#### e) Alerta de Portón Abierto (`GATE_OPEN_TIMEOUT`) y Cierre (`GATE_CLOSED`) — Fase 3
Sensor magnético (Reed Switch): contacto cerrado = portón cerrado. Si permanece abierto más de `threshold_seconds` (45) se publica **una vez** `GATE_OPEN_TIMEOUT`; al cerrarse tras una alerta se publica `GATE_CLOSED`.
```json
{
  "event": "GATE_OPEN_TIMEOUT",
  "schema_version": 1,
  "occurred_at": 1795458700,
  "payload": { "open_seconds": 45, "threshold_seconds": 45 }
}
```
`GATE_CLOSED` usa el mismo payload (`open_seconds` = duración total abierta).

#### f) Reglas de Firmware (Fase 3)
- Al conectar a Mosquitto el ESP32 publica un `HEARTBEAT` con `retain=true` para sobrescribir el LWT retenido (`device_offline`) de una sesión anterior.
- El pulso del relé es **1000 ms** fijo en firmware (`RELAY_PULSE_MS`); el `pulse_ms` del comando `UNLOCK` solo se respeta si se compila con `HONOR_COMMAND_PULSE_MS=1`.
- Reloj: NTP; respaldo = último Unix time persistido en NVS + `millis()` (también se ajusta con `server_unix_time` de `SYNC_CONFIG`). Sin hora válida (< 2024-01-01) el QR se rechaza (fail-closed, `result=ERROR`).
- Resultados offline emitidos: `GRANTED`, `DENIED_WRONG_CONDO`, `DENIED_EXPIRED`, `DENIED_REVOKED`, `DENIED_INVALID_HMAC` (también para payload malformado) y `ERROR`.
- Los `ACCESS_EVENT` generados sin conexión se encolan (RAM, 32 eventos) y se envían al reconectar con su `occurred_at` original.

---

## 7. Contratos de API REST, WebSocket STOMP y Seguridad de Sesión

- **Base URL Local:** `http://<LAN_HOST>/api/v1` (proxeada por `web-admin` Nginx hacia `backend:8080`).
- **Autenticación de Usuarios (Web Admin y App Android):**
  - `POST /api/v1/auth/login` emite `accessToken` + `refreshToken` firmados con `JWT_SECRET` (exclusivo para clientes HTTP/WS; **nunca** utilizado en códigos QR). Los Refresh Tokens se almacenan hasheados (SHA-256) en PostgreSQL (`refresh_tokens`) con rotación y revocación real.
  - `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`.
  - Header estándar: `Authorization: Bearer <accessToken>`.
  - Roles soportados: `SUPER_ADMIN`, `CONDO_ADMIN`, `RESIDENT`.
- **Recursos Principales (Multi-Tenant):**
  - `/api/v1/condominiums` — Alta/gestión de condominios, coordenadas de geofencing y rotación de `hmac_secret`.
  - `/api/v1/condos/{condoId}/units` — Gestión de unidades funcionales, estado de bloqueo (`blocked`), secreto derivado (`unit_secret`) y extensiones de intercomunicador.
  - `/api/v1/condos/{condoId}/users` — Gestión de administradores y residentes (incluyendo bloqueo/desbloqueo).
  - `/api/v1/condos/{condoId}/devices` — Registro de dispositivos ESP32, token QR público de citofonía, estado en vivo y comandos (`POST .../sync-config`).
  - `GET /api/v1/access-passes/unit-secret` — Entrega al residente autenticado su `unit_secret` derivado para emisión de QR offline.
  - `/api/v1/condos/{condoId}/access-passes` — Generación, registro offline (`/register`), validación HMAC (`/validate`), consulta y revocación (`/{passId}/revoke`) de pases QR compactos offline.
  - `POST /api/v1/access/open` — Apertura remota con verificación de bloqueo (usuario/unidad), validación GPS por geofencing (Haversine contra el radio del condominio), publicación MQTT en `condo/{condoId}/device/{deviceId}/command` y registro en `access_logs`.
  - `/api/v1/condos/{condoId}/access-logs` — Auditoría paginada y filtrable de accesos otorgados/denegados.
  - `/api/v1/public/intercom/door/{publicQrToken}` y `POST /api/v1/public/intercom/ring` — Flujo público para visitantes escaneando el QR físico de la puerta.
  - `/api/v1/condos/{condoId}/intercom-calls` — Historial y acciones del residente/admin (`answer`, `unlock`, `reject`, `end`).
- **WebSocket STOMP Local (`/ws/intercom`):**
  - Canales privados (autenticados vía JWT en frame `CONNECT` con aislamiento por `condoId`/`unitId`):
    - `/topic/condo/{condoId}/unit/{unitId}/intercom` (alertas de llamada en tiempo real para residentes de la unidad)
    - `/topic/condo/{condoId}/intercom` (monitor de llamadas para administradores del condominio)
  - Canal público de estado de llamada del visitante: `/topic/public/intercom/call/{callId}`.
  - Destinos de aplicación: `/app/intercom/{callId}/decision` y `/app/intercom/{callId}/signal`.
- **Healthcheck Backend:** `GET /actuator/health`.

---

## 8. Modelo de Datos Relacional (PostgreSQL 16 + Flyway)

Esquema definido en [`backend/src/main/resources/db/migration/V1__init_schema.sql`](backend/src/main/resources/db/migration/V1__init_schema.sql):

| Tabla | Rol en el Dominio | Clave de Aislamiento Multi-Tenant |
| :--- | :--- | :--- |
| `condominiums` | Inquilinos (condominios) y su `hmac_secret` versionado | `id` (raíz del tenant) |
| `units` | Departamentos / lotes y extensión del intercomunicador | `condominium_id` + `UNIQUE (id, condominium_id)` |
| `users` | Administradores y residentes autenticados | `condominium_id` + FK compuesta a `units` |
| `iot_devices` | Controladores ESP32 (portones, puertas, intercomunicadores) | `condominium_id` + `UNIQUE (id, condominium_id)` |
| `access_passes` | Pases QR compactos offline emitidos y su ciclo de vida | `condominium_id` + FKs compuestas a `units` y `users` |
| `access_logs` | Bitácora inmutable de accesos (online y sincronizados offline) | `condominium_id` + FKs compuestas opcionales |
| `intercom_calls` | Registro y señalización LAN de llamadas portero-residente | `condominium_id` + FKs compuestas a `iot_devices`, `units` y `users` |

---

## 9. Seguridad Operativa en Red Local

1. **Mosquitto sin acceso anónimo:** `allow_anonymous false` y `password_file /mosquitto/config/pwfile` obligatorios en `mosquitto/config/mosquitto.conf`.
2. **Aislamiento de Base de Datos:** PostgreSQL solo es accesible en la red Docker `accesolibre-net` y opcionalmente en `127.0.0.1:5432` del host servidor.
3. **Rotación de Claves HMAC:** Cada condominio mantiene `hmac_key_version` y cada dispositivo registra `hmac_key_version_synced`, permitiendo detectar controladores desincronizados antes de rotar secretos.
4. **Contenedores sin privilegios innecesarios:** El servicio `backend` ejecuta la JVM bajo un usuario no-root dedicado (`accesolibre`).
