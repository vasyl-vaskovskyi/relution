# syntax=docker/dockerfile:1
# Images, build argument and health check: docs/operations/deployment.md

FROM node:24.21.0-alpine3.24 AS build
WORKDIR /workspace

# Dependencies first, so the npm layer stays cached while sources change
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY angular.json tsconfig.json tsconfig.app.json ./
COPY public public
COPY src src

# production (default) or demo (debug logging with the runtime override, used by local compose)
ARG ANGULAR_CONFIGURATION=production
RUN npm run build -- --configuration "${ANGULAR_CONFIGURATION}"

FROM nginxinc/nginx-unprivileged:1.30.4-alpine3.24

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser/ /usr/share/nginx/html/

# The base image already runs as the unprivileged nginx user
EXPOSE 8080
