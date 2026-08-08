# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /app

# Build-time API base URL. Setting this to "/api" makes the SPA rely on the
# nginx reverse proxy in this same image, so the browser never needs to know
# the backend's DNS name.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

# --- Runtime stage: nginx serving the SPA + reverse-proxying /api to the backend ---
FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
