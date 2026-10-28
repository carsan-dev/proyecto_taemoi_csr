# Build stage with Alpine for smaller image
FROM node:22-alpine AS build
WORKDIR /app

# Copy package files first for better layer caching
COPY package*.json ./

# Use npm ci for faster, more reliable builds
RUN npm ci --only=production=false && npm cache clean --force

# Copy source code
COPY . .

# Build for production
RUN npm run build:production

# Production stage with Alpine nginx + Brotli support
FROM fholzer/nginx-brotli:v1.30.6

# Install curl for healthcheck
RUN apk add --no-cache curl

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy built Angular app
COPY --from=build /app/dist/taemoi-angular-front/browser /usr/share/nginx/html

# Copy nginx configuration
COPY ./nginx.conf /etc/nginx/nginx.conf
COPY ./default.conf /etc/nginx/conf.d/default.conf
# Cabeceras de seguridad que incluye default.conf (fuera de conf.d: nginx.conf incluye conf.d/*.conf a nivel http)
COPY ./nginx-cabeceras-seguridad.conf /etc/nginx/cabeceras-seguridad.conf

# Set proper permissions
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost/ || exit 1

EXPOSE 80

CMD ["-g", "daemon off;"]
