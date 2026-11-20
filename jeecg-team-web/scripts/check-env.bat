@echo off
chcp 65001 >nul
setlocal
rem ============================================================
rem 环境快速检查与配置：Node ^>= 20.19、pnpm ^>= 10，缺则提示安装方式
rem 通过后自动 pnpm install。双击运行。
rem ============================================================

cd /d "%~dp0.."

echo === Node 检查 ===
where node >nul 2>nul
if errorlevel 1 (
  echo [缺失] Node.js 未安装。
  echo        请到 https://nodejs.org 安装 LTS 版（^>= 20.19），装完重新运行本脚本。
  pause
  exit /b 1
)
for /f "delims=" %%v in ('node -v') do set "NODE_V=%%v"
echo 发现 Node %NODE_V%
node -e "process.exit(parseInt(process.versions.node.split('.')[0])>=20 && parseInt(process.versions.node.split('.')[1])>=19 ? 0 : (parseInt(process.versions.node.split('.')[0])>20?0:1))"
if errorlevel 1 (
  echo [版本不足] 需要 Node ^>= 20.19，当前 %NODE_V%。请先升级 Node。
  pause
  exit /b 1
)

echo === pnpm 检查 ===
where pnpm >nul 2>nul
if errorlevel 1 (
  echo [缺失] pnpm 未安装，尝试用 corepack 启用...
  call corepack enable >nul 2>nul
  call corepack prepare pnpm@latest-10 --activate >nul 2>nul
  where pnpm >nul 2>nul
  if errorlevel 1 (
    echo [失败] corepack 启用失败。请管理员权限执行：npm i -g pnpm
    pause
    exit /b 1
  )
)
for /f "delims=" %%v in ('pnpm -v') do set "PNPM_V=%%v"
echo 发现 pnpm %PNPM_V%

echo === 安装依赖 ===
call pnpm install
if errorlevel 1 (
  echo [失败] pnpm install 出错，检查网络后重试。
  pause
  exit /b 1
)

echo.
echo 环境就绪。运行 scripts\start-dev.bat 启动前端。
pause
