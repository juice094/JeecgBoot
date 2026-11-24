# 现行默认前端（团队协作工作台）nginx 一体化镜像
# 构建：cd jeecg-team-web && pnpm install && pnpm build 已含在镜像内，无需宿主预构建
FROM registry.cn-hangzhou.aliyuncs.com/dockerhub_mirror/node:20-alpine AS build

WORKDIR /app

# 先拷锁文件装依赖，最大化利用构建缓存
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM registry.cn-hangzhou.aliyuncs.com/dockerhub_mirror/nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
