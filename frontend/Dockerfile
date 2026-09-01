# TODO: restore the Node build stage once the Angular project is scaffolded here.
# ── Stage 1: Build ─────────────────────────────────────────────────────────
# Angular 21 requires Node 22.22.3+ or 24.15.0+.
# FROM node:24-alpine AS build
#
# WORKDIR /app
#
# # Copy only package files first so npm ci is cached unless deps change.
# COPY package*.json ./
# RUN npm ci
#
# COPY . .
# RUN npx ng build --configuration production

# ── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM nginx:1.27-alpine

# Angular CLI outputs to dist/<project-name>/browser as of Angular 17+.
# COPY --from=build /app/dist/jeepers-leapers-frontend/browser /usr/share/nginx/html
# nginx.conf not applied yet — image's built-in default.conf serves the stock welcome page.
# COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
