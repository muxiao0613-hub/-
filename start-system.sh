#!/bin/bash

echo "========================================"
echo "多平台数据采集与AI分析系统启动脚本"
echo "========================================"

echo
echo "检查环境..."

# 检查Java
if ! command -v java &> /dev/null; then
    echo "[错误] 未找到Java环境，请先安装Java 17+"
    exit 1
fi

# 检查Node.js
if ! command -v node &> /dev/null; then
    echo "[错误] 未找到Node.js环境，请先安装Node.js"
    exit 1
fi

echo "[✓] Java环境检查通过"
echo "[✓] Node.js环境检查通过"

# 检查OpenAI API密钥
if [ -z "$OPENAI_API_KEY" ]; then
    echo
    echo "[警告] 未检测到OPENAI_API_KEY环境变量"
    echo "请设置您的OpenAI API密钥以启用AI功能："
    echo "export OPENAI_API_KEY=\"sk-your-api-key-here\""
    echo
    echo "继续启动系统（将使用本地分析模式）..."
    sleep 3
else
    echo "[✓] OpenAI API密钥已配置"
fi

echo
echo "启动后端服务..."
cd BackEnd
gnome-terminal --title="后端服务" -- bash -c "./mvnw spring-boot:run; exec bash" 2>/dev/null || \
xterm -title "后端服务" -e "./mvnw spring-boot:run" 2>/dev/null || \
./mvnw spring-boot:run &

echo "等待后端服务启动..."
sleep 10

echo
echo "启动前端服务..."
cd ../FrontEnd

# 检查是否已安装依赖
if [ ! -d "node_modules" ]; then
    echo "安装前端依赖..."
    npm install
fi

gnome-terminal --title="前端服务" -- bash -c "npm run dev; exec bash" 2>/dev/null || \
xterm -title "前端服务" -e "npm run dev" 2>/dev/null || \
npm run dev &

echo
echo "========================================"
echo "系统启动完成！"
echo "========================================"
echo "后端服务: http://localhost:8080"
echo "前端界面: http://localhost:5173"
echo
echo "功能说明:"
echo "1. 数据上传 - 上传Excel文件"
echo "2. 数据分析 - 查看分析结果"
echo "3. 异常检测 - 检测数据异常"
echo "4. AI助手   - AI聊天互动"
echo
echo "按Ctrl+C退出..."

# 等待用户中断
trap 'echo "正在关闭服务..."; kill $(jobs -p) 2>/dev/null; exit' INT
wait