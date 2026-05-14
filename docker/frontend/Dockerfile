FROM node:24-alpine AS build
WORKDIR /workspace
COPY qs-ai-admin-web/package*.json ./
RUN npm install
COPY qs-ai-admin-web/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY QS-AI-ADMIAN/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
