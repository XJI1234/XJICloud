# 多阶段构建：用户前端 + 管理面板 → Nginx 静态托管
# 构建上下文为仓库根目录：
#   docker build -f deploy/k8s/frontend/Dockerfile -t xjicloud/frontend:local .

FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
COPY admin/package.json admin/package-lock.json ./admin/
RUN npm ci --legacy-peer-deps && cd admin && npm ci --legacy-peer-deps

COPY . .
# SuperSplat 子模块可能未 checkout；跳过 build:supersplat，仅构建主站与管理端
RUN npm run build && cd admin && npm run build

FROM nginx:1.27-alpine
COPY deploy/k8s/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html/dist
COPY --from=build /app/admin/dist /usr/share/nginx/html/admin/dist
EXPOSE 80
