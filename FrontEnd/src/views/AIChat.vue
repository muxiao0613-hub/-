<template>
  <div class="ai-chat-container">
    <!-- 头部 -->
    <div class="chat-header">
      <h2>🤖 AI运营助手</h2>
      <div class="header-actions">
        <el-button 
          type="primary" 
          size="small" 
          @click="initializeChat"
          :loading="initializing"
        >
          {{ initializing ? '初始化中...' : '重新分析数据' }}
        </el-button>
        <el-button 
          type="warning" 
          size="small" 
          @click="clearHistory"
        >
          清空历史
        </el-button>
      </div>
    </div>

    <!-- 快捷命令 -->
    <div class="quick-commands" v-if="quickCommands.length > 0">
      <div class="commands-title">💡 快捷命令：</div>
      <div class="commands-list">
        <el-tag 
          v-for="(command, key) in quickCommands" 
          :key="key"
          class="command-tag"
          @click="sendQuickCommand(key)"
          type="info"
        >
          /{{ key }}
        </el-tag>
      </div>
    </div>

    <!-- 聊天区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div 
        v-for="(message, index) in messages" 
        :key="index"
        :class="['message', message.role]"
      >
        <div class="message-avatar">
          <i :class="message.role === 'user' ? 'el-icon-user' : 'el-icon-cpu'"></i>
        </div>
        <div class="message-content">
          <div class="message-text" v-html="formatMessage(message.content)"></div>
          <div class="message-time">{{ formatTime(message.timestamp) }}</div>
        </div>
      </div>
      
      <!-- 加载状态 -->
      <div v-if="loading" class="message assistant">
        <div class="message-avatar">
          <i class="el-icon-cpu"></i>
        </div>
        <div class="message-content">
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="3"
        placeholder="输入您的问题，或使用快捷命令如 /内容策略..."
        @keydown.ctrl.enter="sendMessage"
        :disabled="loading"
      />
      <div class="input-actions">
        <div class="input-tips">
          <span>Ctrl + Enter 发送</span>
          <span v-if="!aiAvailable" class="ai-status warning">⚠️ AI服务未配置</span>
          <span v-else class="ai-status success">✅ AI服务已启用</span>
        </div>
        <el-button 
          type="primary" 
          @click="sendMessage"
          :loading="loading"
          :disabled="!inputMessage.trim()"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

export default {
  name: 'AIChat',
  setup() {
    const messages = ref([])
    const inputMessage = ref('')
    const loading = ref(false)
    const initializing = ref(false)
    const sessionId = ref('')
    const aiAvailable = ref(false)
    const quickCommands = ref({})
    const messagesContainer = ref(null)

    // 初始化
    onMounted(async () => {
      sessionId.value = generateSessionId()
      await loadQuickCommands()
      await initializeChat()
    })

    // 生成会话ID
    const generateSessionId = () => {
      return 'chat_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
    }

    // 加载快捷命令
    const loadQuickCommands = async () => {
      try {
        const response = await api.get('/multiplatform/chat/quick-commands')
        if (response.data.success) {
          quickCommands.value = response.data.commands
        }
      } catch (error) {
        console.error('加载快捷命令失败:', error)
      }
    }

    // 初始化聊天
    const initializeChat = async () => {
      initializing.value = true
      try {
        const response = await api.post('/multiplatform/chat/initialize', {
          sessionId: sessionId.value
        })
        
        if (response.data.success) {
          messages.value = response.data.history || []
          aiAvailable.value = response.data.aiAvailable
          ElMessage.success('AI助手已准备就绪')
        } else {
          ElMessage.error(response.data.message || '初始化失败')
        }
      } catch (error) {
        console.error('初始化聊天失败:', error)
        ElMessage.error('初始化失败，请检查网络连接')
      } finally {
        initializing.value = false
        await nextTick()
        scrollToBottom()
      }
    }

    // 发送消息
    const sendMessage = async () => {
      if (!inputMessage.value.trim() || loading.value) return

      const userMessage = inputMessage.value.trim()
      inputMessage.value = ''

      // 添加用户消息到界面
      messages.value.push({
        role: 'user',
        content: userMessage,
        timestamp: new Date()
      })

      loading.value = true
      await nextTick()
      scrollToBottom()

      try {
        const response = await api.post('/multiplatform/chat', {
          message: userMessage,
          sessionId: sessionId.value
        })

        if (response.data.success) {
          // 添加AI回复
          messages.value.push({
            role: 'assistant',
            content: response.data.response,
            timestamp: new Date()
          })
          aiAvailable.value = response.data.aiAvailable
        } else {
          ElMessage.error(response.data.message || '发送失败')
        }
      } catch (error) {
        console.error('发送消息失败:', error)
        ElMessage.error('发送失败，请重试')
      } finally {
        loading.value = false
        await nextTick()
        scrollToBottom()
      }
    }

    // 发送快捷命令
    const sendQuickCommand = (command) => {
      inputMessage.value = '/' + command
      sendMessage()
    }

    // 清空历史
    const clearHistory = async () => {
      try {
        const response = await api.post('/multiplatform/chat/clear', {
          sessionId: sessionId.value
        })
        
        if (response.data.success) {
          messages.value = []
          ElMessage.success('对话历史已清空')
        }
      } catch (error) {
        console.error('清空历史失败:', error)
        ElMessage.error('清空失败')
      }
    }

    // 格式化消息（支持Markdown）
    const formatMessage = (content) => {
      if (!content) return ''
      
      // 简单的Markdown转换
      return content
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/`(.*?)`/g, '<code>$1</code>')
        .replace(/\n/g, '<br>')
        .replace(/#{1,6}\s*(.*)/g, '<h3>$1</h3>')
        .replace(/- (.*)/g, '• $1')
    }

    // 格式化时间
    const formatTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit' 
      })
    }

    // 滚动到底部
    const scrollToBottom = () => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    }

    return {
      messages,
      inputMessage,
      loading,
      initializing,
      aiAvailable,
      quickCommands,
      messagesContainer,
      initializeChat,
      sendMessage,
      sendQuickCommand,
      clearHistory,
      formatMessage,
      formatTime
    }
  }
}
</script>

<style scoped>
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-height: 800px;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.chat-header h2 {
  margin: 0;
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.quick-commands {
  padding: 12px 20px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
}

.commands-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.commands-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.command-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.command-tag:hover {
  background: #409eff;
  color: white;
}

.chat-messages {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
  background: #f5f7fa;
}

.message {
  display: flex;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease-in;
}

.message.user {
  justify-content: flex-end;
}

.message.user .message-content {
  background: #409eff;
  color: white;
  margin-left: 60px;
}

.message.assistant .message-content {
  background: white;
  margin-right: 60px;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e4e7ed;
  margin: 0 8px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #409eff;
  color: white;
  order: 1;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.message-text {
  line-height: 1.5;
  word-wrap: break-word;
}

.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

.chat-input {
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e4e7ed;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-tips {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.ai-status.success {
  color: #67c23a;
}

.ai-status.warning {
  color: #e6a23c;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-10px); }
}

/* 响应式 */
@media (max-width: 768px) {
  .message-content {
    max-width: 85%;
  }
  
  .commands-list {
    gap: 4px;
  }
  
  .command-tag {
    font-size: 12px;
    padding: 2px 6px;
  }
}
</style>