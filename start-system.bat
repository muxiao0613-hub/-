@echo off
echo ========================================
echo 多平台数据采集与AI分析系统启动脚本
echo ========================================

echo.
echo 检查环境...

:: 检查Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到Java环境，请先安装Java 17+
    pause
    exit /b 1
)

:: 检查Node.js
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到Node.js环境，请先安装Node.js
    pause
    exit /b 1
)

echo [✓] Java环境检查通过
echo [✓] Node.js环境检查通过

:: 设置OpenAI API密钥（如果未设置）
if "%OPENAI_API_KEY%"=="" (
    echo.
    echo [警告] 未检测到OPENAI_API_KEY环境变量
    echo 请设置您的OpenAI API密钥以启用AI功能：
    echo set OPENAI_API_KEY=sk-your-api-key-here
    echo.
    echo 继续启动系统（将使用本地分析模式）...
    timeout /t 3 >nul
) else (
    echo [✓] OpenAI API密钥已配置
)

echo.
echo 启动后端服务...
cd BackEnd
start "后端服务" cmd /k "mvnw.cmd spring-boot:run"

echo 等待后端服务启动...
timeout /t 10 >nul

echo.
echo 启动前端服务...
cd ..\FrontEnd

:: 检查是否已安装依赖
if not exist node_modules (
    echo 安装前端依赖...
    npm install
)

start "前端服务" cmd /k "npm run dev"

echo.
echo ========================================
echo 系统启动完成！
echo ========================================
echo 后端服务: http://localhost:8080
echo 前端界面: http://localhost:5173
echo.
echo 功能说明:
echo 1. 数据上传 - 上传Excel文件
echo 2. 数据分析 - 查看分析结果
echo 3. 异常检测 - 检测数据异常
echo 4. AI助手   - AI聊天互动
echo.
echo 按任意键退出...
pause >nul