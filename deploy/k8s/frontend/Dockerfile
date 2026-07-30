# 多阶段构建：用户前端 + 管理面板 → Nginx 静态托管
# 构建上下文为仓库根目录：
#   docker build -f deploy/k8s/frontend/Dockerfile -t xjicloud/frontend:local .

FROM node:20-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/web/package.json ./apps/web/
COPY apps/admin/package.json ./apps/admin/
COPY packages/spark/package.json ./packages/spark/
COPY packages/shared/package.json ./packages/shared/
RUN pnpm install --frozen-lockfile

COPY . .
# web prebuild 会从 vendors/supersplat 复制预编译静态资源到 public/supersplat
RUN pnpm run build:web && pnpm run build:admin

FROM nginx:1.27-alpine
COPY deploy/k8s/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/web/dist /usr/share/nginx/html/dist
# Vite base=/admin/，产物需落在 /admin/ 而非 /admin/dist/
COPY --from=build /app/apps/admin/dist /usr/share/nginx/html/admin
EXPOSE 80
