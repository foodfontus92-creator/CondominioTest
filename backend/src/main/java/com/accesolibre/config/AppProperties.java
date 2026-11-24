package com.accesolibre.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "accesolibre")
public class AppProperties {

    private Security security = new Security();
    private Mqtt mqtt = new Mqtt();
    private Qr qr = new Qr();

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Mqtt getMqtt() { return mqtt; }
    public void setMqtt(Mqtt mqtt) { this.mqtt = mqtt; }

    public Qr getQr() { return qr; }
    public void setQr(Qr qr) { this.qr = qr; }

    public static class Security {
        private String jwtSecret;
        private long accessTokenTtlSeconds = 900;
        private long refreshTokenTtlSeconds = 2592000;
        private String corsAllowedOrigins = "*";

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

        public long getAccessTokenTtlSeconds() { return accessTokenTtlSeconds; }
        public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) { this.accessTokenTtlSeconds = accessTokenTtlSeconds; }

        public long getRefreshTokenTtlSeconds() { return refreshTokenTtlSeconds; }
        public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) { this.refreshTokenTtlSeconds = refreshTokenTtlSeconds; }

        public String getCorsAllowedOrigins() { return corsAllowedOrigins; }
        public void setCorsAllowedOrigins(String corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }
    }

    public static class Mqtt {
        private boolean enabled = true;
        private String brokerUrl = "tcp://localhost:1883";
        private String username;
        private String password;
        private String clientId = "accesolibre-backend";
        private int qos = 1;
        private int connectionTimeoutSeconds = 10;
        private int keepAliveIntervalSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBrokerUrl() { return brokerUrl; }
        public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public int getQos() { return qos; }
        public void setQos(int qos) { this.qos = qos; }

        public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) { this.connectionTimeoutSeconds = connectionTimeoutSeconds; }

        public int getKeepAliveIntervalSeconds() { return keepAliveIntervalSeconds; }
        public void setKeepAliveIntervalSeconds(int keepAliveIntervalSeconds) { this.keepAliveIntervalSeconds = keepAliveIntervalSeconds; }
    }

    public static class Qr {
        private int maxPayloadLength = 119;
        private int hmacTruncatedBytes = 12;
        private int defaultPassTtlHours = 24;
        private long clockSkewSeconds = 60;

        public int getMaxPayloadLength() { return maxPayloadLength; }
        public void setMaxPayloadLength(int maxPayloadLength) { this.maxPayloadLength = maxPayloadLength; }

        public int getHmacTruncatedBytes() { return hmacTruncatedBytes; }
        public void setHmacTruncatedBytes(int hmacTruncatedBytes) { this.hmacTruncatedBytes = hmacTruncatedBytes; }

        public int getDefaultPassTtlHours() { return defaultPassTtlHours; }
        public void setDefaultPassTtlHours(int defaultPassTtlHours) { this.defaultPassTtlHours = defaultPassTtlHours; }

        public long getClockSkewSeconds() { return clockSkewSeconds; }
        public void setClockSkewSeconds(long clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }
    }
}
