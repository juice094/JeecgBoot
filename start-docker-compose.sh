#!/bin/bash
# JEECG Boot 一键启动脚本 (Linux Bash 版，2026-11-24 对齐现行栈：team-web 镜像内构建，端口 8081/3200)

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo
echo -e "[1/4] 检查必要工具..."

# 检查必要工具
command -v docker > /dev/null 2>&1 || { echo -e "${RED}[错误] 未安装 docker，请先安装 Docker Desktop${NC}"; exit 1; }
docker compose version > /dev/null 2>&1 || { echo -e "${RED}[错误] 未安装 docker compose 插件${NC}"; exit 1; }
command -v mvn > /dev/null 2>&1 || { echo -e "${RED}[错误] 未安装 Maven${NC}"; exit 1; }

echo -e "[2/4] 编译后端项目（激活 docker profile，过滤出 application-docker.yml）..."
cd jeecg-boot
mvn clean install -Pdocker

echo -e "[3/4] 构建并启动全栈容器（team-web 前端在镜像内构建，无需宿主 pnpm）..."
cd ..
docker compose up -d --build

echo
echo "========================================"
echo "  JEECG Boot 启动成功 (请等待1-2分钟，待 healthcheck 全部通过）"
echo "========================================"
echo "前端访问:     http://localhost:3200"
echo "后端API:      http://localhost:8081/jeecg-boot"
echo "接口文档:     http://localhost:8081/jeecg-boot/doc.html"
echo "MinIO 控制台: http://localhost:9003（minioadmin/minioadmin）"
echo
