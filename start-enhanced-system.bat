@echo off
echo ========================================
echo 启动增强版电商流量异常分析系统
echo ========================================

echo.
echo 正在创建必要的目录...
if not exist "downloads" mkdir downloads
if not exist "downloads\images" mkdir downloads\images
if not exist "data" mkdir data

echo.
echo 正在启动后端服务...
cd BackEnd
start "后端服务" cmd /k "mvnw.cmd spring-boot:run"

echo.
echo 等待后端服务启动...
timeout /t 10 /nobreak > nul

echo.
echo 正在启动前端服务...
cd ..\FrontEnd
start "前端服务" cmd /k "npm run dev"

echo.
echo ========================================
echo 系统启动完成！
echo ========================================
echo.
echo 后端服务: http://localhost:8080
echo 前端界面: http://localhost:3000
echo H2数据库控制台: http://localhost:8080/h2-console
echo.
echo 新增功能:
echo - AI智能建议生成
echo - 图片自动下载和分析
echo - 增强的异常检测算法
echo - 分离式的原因分析和优化建议
echo.
echo 按任意键退出...
pause > nul