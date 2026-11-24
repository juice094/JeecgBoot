@echo off
:: JEECG Boot 一键启动脚本 (Windows CMD 版，2026-11-24 对齐现行栈：team-web 镜像内构建，端口 8081/3200)
chcp 65001 > nul

echo.
echo [1/3] 检查必要工具...
where docker > nul 2>&1 || (
    echo [错误] 未安装 docker，请先安装 Docker Desktop
    pause
    exit /b 1
)
docker compose version > nul 2>&1 || (
    echo [错误] 未安装 docker compose 插件
    pause
    exit /b 1
)
where mvn > nul 2>&1 || (
    echo [错误] 未安装 Maven
    pause
    exit /b 1
)

echo [2/3] 编译后端项目（激活 docker profile，过滤出 application-docker.yml）...
cd jeecg-boot
call mvn clean install -Pdocker
if %errorlevel% neq 0 (
    echo [错误] 后端编译失败！
    pause
    exit /b 1
)
cd ..

echo [3/3] 构建并启动全栈容器（team-web 前端在镜像内构建，无需宿主 pnpm）...
docker compose up -d --build

echo.
echo ========================================
echo   JEECG Boot 启动成功 (请等待1-2分钟，待 healthcheck 全部通过）
echo ========================================
echo 前端访问:     http://localhost:3200
echo 后端API:      http://localhost:8081/jeecg-boot
echo 接口文档:     http://localhost:8081/jeecg-boot/doc.html
echo MinIO 控制台: http://localhost:9003（minioadmin/minioadmin）
echo.
pause
