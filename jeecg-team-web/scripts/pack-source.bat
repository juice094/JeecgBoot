@echo off
chcp 65001 >nul
setlocal
rem ============================================================
rem 打包前端源码（排除中间构建产物），供微信等渠道传给队友
rem 产物：项目上级目录 jeecg-team-web-src-YYYYMMDD.zip
rem 双击运行即可；需要 PowerShell（Win10/11 自带）+ robocopy（系统自带）
rem ============================================================

for %%I in ("%~dp0..") do set "PROJ=%%~fI"
for %%I in ("%PROJ%\..") do set "PARENT=%%~fI"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd"') do set "TODAY=%%i"

set "SRC=%PROJ%"
set "STAGE=%TEMP%\jeecg-team-web-pack"
set "OUT=%PARENT%\jeecg-team-web-src-%TODAY%.zip"

if exist "%OUT%" (
  echo [提示] 已存在同名包，先删除旧包...
  del /f /q "%OUT%"
)

echo [1/3] 暂存源码（排除 node_modules / dist / .git / target 等）...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
rem /XD 只写目录名，任意层级同名即排除；不要带路径分隔符（跨 shell 调用会变形）
robocopy "%SRC%" "%STAGE%\jeecg-team-web" /E /NFL /NDL /NJH /NJS ^
  /XD node_modules dist .git .idea .vscode target ^
  /XF *.log .DS_Store Thumbs.db "jeecg-team-web-src-*.zip" >nul
if errorlevel 8 ( echo [失败] 暂存出错 & exit /b 1 )

echo [2/3] 压缩...
powershell -NoProfile -Command "Compress-Archive -Path '%STAGE%\jeecg-team-web' -DestinationPath '%OUT%' -CompressionLevel Optimal -Force"
if errorlevel 1 ( echo [失败] 压缩出错 & rmdir /s /q "%STAGE%" & exit /b 1 )

echo [3/3] 清理暂存...
rmdir /s /q "%STAGE%"

for %%I in ("%OUT%") do set "SIZE=%%~zI"
set /a "SIZE_MB=%SIZE%/1048576"
echo.
echo 完成：%OUT%（约 %SIZE_MB% MB）
if %SIZE_MB% GTR 100 echo [注意] 超过 100MB，微信可能传不动，请检查是否混入了产物目录。
endlocal
