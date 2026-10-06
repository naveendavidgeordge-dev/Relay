# Relay console (Next.js 14 standalone). Build context: repository root (npm workspaces).

FROM node:20-alpine AS deps
WORKDIR /src
COPY package.json package-lock.json ./
COPY frontend/package.json ./frontend/package.json
RUN npm ci --workspace frontend --include-workspace-root --no-audit --no-fund

FROM node:20-alpine AS build
WORKDIR /src
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /src/node_modules ./node_modules
COPY package.json package-lock.json ./
COPY frontend ./frontend
RUN npm --workspace frontend run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S relay && adduser -S relay -G relay
COPY --from=build --chown=relay:relay /src/frontend/.next/standalone ./
COPY --from=build --chown=relay:relay /src/frontend/.next/static ./frontend/.next/static
COPY --from=build --chown=relay:relay /src/frontend/public ./frontend/public
USER relay
EXPOSE 3000
CMD ["node", "frontend/server.js"]
