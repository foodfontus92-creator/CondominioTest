# syntax=docker/dockerfile:1.7

# =============================================================================
# Stage 1: Build Web Admin SPA (Node 22 Alpine + Vite + Tailwind)
# =============================================================================
FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    if [ -f package-lock.json ]; then \
      npm ci --no-audit --no-fund; \
    else \
      npm install --no-audit --no-fund; \
    fi

COPY . ./
ARG VITE_API_URL=https://api-accesolibre.fodtech.cl
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# =============================================================================
# Stage 2: Production Static Server + LAN Reverse Proxy (Nginx 1.27 Alpine)
# =============================================================================
FROM nginx:1.27-alpine AS runtime

RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:80/healthz >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
