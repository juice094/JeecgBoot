@echo off
chcp 65001 >nul
setlocal
rem ============================================================
rem 快速启动前端开发服务器（http://127.0.0.1:3200）
rem 前置：先跑过一次 scripts\check-env.bat（依赖装好即可，不必重复跑）
rem 注意：本脚本只起前端；后端 jeecg-boot 需在 8081 另行启动
rem       （后端启动见 JeecgBoot 仓库 AGENTS.md，账号 admin/123456）
rem ============================================================

cd /d "%~dp0.."

if not exist node_modules (
  echo [提示] 首次运行，先安装依赖...
  call pnpm install
  if errorlevel 1 ( echo [失败] 依赖安装失败 & pause & exit /b 1 )
)

netstat -ano | findstr ":8081" | findstr LISTENING >nul
if errorlevel 1 (
  echo [注意] 未发现 8081 后端在运行，登录与业务接口将不可用。
  echo        如需全链路联调，请先启动 jeecg-boot（--server.port=8081）。
  echo.
)

echo 启动前端 dev server：http://127.0.0.1:3200
call pnpm dev
endlocal
