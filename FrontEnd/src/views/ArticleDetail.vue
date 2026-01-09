<template>
  <div class="detail-page">
    <div class="top-nav">
      <el-button @click="goBack" :icon="ArrowLeft" circle />
      <h2 class="page-title">{{ article?.title || '文章详情' }}</h2>
    </div>

    <div class="detail-layout" v-loading="loading">
      <div class="left-column">
        <div class="top-blank-area">
          <div class="article-meta">
            <el-tag>{{ article?.brand }}</el-tag>
            <el-tag type="info">{{ article?.postType }}</el-tag>
            <el-tag type="warning">{{ article?.contentType }}</el-tag>
            <span class="publish-time">{{ formatDate(article?.publishTime) }}</span>
          </div>
          <div class="article-link" v-if="article?.articleLink">
            <a :href="article.articleLink" target="_blank" class="link-text">
              <el-icon><Link /></el-icon>
              查看原文
            </a>
          </div>
        </div>

        <el-card class="content-card">
          <template #header>
            <div class="card-header">
              <span>📷 图文内容</span>
              <!-- 显示爬取状态 -->
              <el-tag 
                :type="article?.crawlStatus === 'SUCCESS' ? 'success' : 'info'" 
                size="small"
              >
                {{ getCrawlStatusText(article?.crawlStatus) }}
              </el-tag>
              <el-tag type="info" size="small" style="margin-left: 8px;">
                {{ article?.platform || '未知平台' }}
              </el-tag>
            </div>
          </template>

          <div v-if="parsedImages.length > 0" class="images-grid">
            <div v-for="(img, idx) in parsedImages" :key="idx" class="image-item">
              <img
                v-if="img.url"
                :src="img.url"
                :alt="img.description || img.alt || '内容图片'"
                @error="handleImageError"
              />
              <div v-else class="image-placeholder">
                <el-icon><Picture /></el-icon>
                <span>图片链接无效</span>
              </div>
              <div class="image-info">
                <span class="image-type">{{ img.type || '内容图' }}</span>
                <span class="image-dimensions" v-if="img.width && img.height">{{ img.width }}×{{ img.height }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="正在自动获取图片内容..." :image-size="80" />

          <div class="text-content" v-if="article?.content">
            <h4>📝 文字内容</h4>
            <div class="content-text">{{ article.content }}</div>
          </div>
          <div class="text-content" v-else>
            <el-empty description="正在自动获取文字内容..." :image-size="60" />
          </div>
        </el-card>

        <el-card class="data-card">
          <template #header>
            <span>📊 数据显示</span>
          </template>

          <div class="data-grid">
            <div class="data-section">
              <h4>7天数据</h4>
              <div class="metrics">
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.readCount7d) }}</span>
                  <span class="metric-label">阅读/播放</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.interactionCount7d) }}</span>
                  <span class="metric-label">互动</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productVisit7d) }}</span>
                  <span class="metric-label">好物访问</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productWant7d) }}</span>
                  <span class="metric-label">好物想要</span>
                </div>
              </div>
            </div>

            <div class="data-section">
              <h4>14天数据</h4>
              <div class="metrics">
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.readCount14d) }}</span>
                  <span class="metric-label">阅读/播放</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.interactionCount14d) }}</span>
                  <span class="metric-label">互动</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productVisitCount) }}</span>
                  <span class="metric-label">好物访问</span>
                </div>
                <div class="metric-item">
                  <span class="metric-value">{{ formatNumber(article?.productWant14d) }}</span>
                  <span class="metric-label">好物想要</span>
                </div>
              </div>
            </div>

            <div class="data-section rates">
              <h4>关键指标</h4>
              <div class="rate-items">
                <div class="rate-item">
                  <span class="rate-label">互动率</span>
                  <span class="rate-value" :class="getInteractionRateClass()">{{ interactionRate }}%</span>
                </div>
                <div class="rate-item">
                  <span class="rate-label">转化率</span>
                  <span class="rate-value">{{ conversionRate }}%</span>
                </div>
                <div class="rate-item">
                  <span class="rate-label">异常状态</span>
                  <el-tag :type="getAnomalyTagType(article?.anomalyStatus)" size="small">
                    {{ getAnomalyStatusText(article?.anomalyStatus) }}
                  </el-tag>
                </div>
                <div class="rate-item">
                  <span class="rate-label">异常评分</span>
                  <span class="rate-value" :class="getScoreClass(article?.anomalyScore)">
                    {{ article?.anomalyScore?.toFixed(1) || '-' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 基础优化建议移到左边 -->
        <el-card class="optimization-card">
          <template #header>
            <div class="card-header">
              <span>💡 基础优化建议</span>
              <el-tag v-if="article?.optimizationSuggestions" type="success" size="small">
                已生成
              </el-tag>
              <el-tag v-else type="info" size="small">
                自动生成中
              </el-tag>
            </div>
          </template>

          <div v-if="article?.optimizationSuggestions" class="optimization-content">
            <pre class="optimization-text">{{ article.optimizationSuggestions }}</pre>
          </div>
          <div v-else class="optimization-empty">
            <el-empty description="正在自动生成基础优化建议..." :image-size="60" />
          </div>
        </el-card>
      </div>
      
      <div class="right-column">
        <!-- AI智能建议 -->
        <el-card class="ai-card">
          <template #header>
            <div class="card-header">
              <span>🤖 AI智能建议</span>
              <div class="header-actions">
                <el-tag :type="aiAvailable ? 'success' : 'warning'" size="small">
                  {{ aiAvailable ? '通义千问已连接' : '本地模式' }}
                </el-tag>
              </div>
            </div>
          </template>

          <div v-if="article?.aiSuggestions" class="ai-content">
            <pre class="ai-text">{{ article.aiSuggestions }}</pre>
            <div class="ai-actions">
              <el-button type="primary" size="small" @click="generateAI" :loading="generatingAI">
                重新生成
              </el-button>
              <el-button type="success" size="small" @click="exportAI" :loading="exporting">
                <el-icon><Download /></el-icon>
                导出Word
              </el-button>
            </div>
          </div>
          <div v-else class="ai-empty">
            <div class="ai-icon-large">🤖</div>
            <p class="ai-hint">点击下方按钮，AI将根据这篇推文的图文内容和数据表现给出针对性优化建议</p>
            <el-button type="primary" size="large" @click="generateAI" :loading="generatingAI">
              {{ generatingAI ? '正在生成...' : '获取AI建议' }}
            </el-button>
            <p class="ai-note" v-if="!aiAvailable">
              ⚠️ AI服务未配置，将使用本地分析模式
            </p>
          </div>
        </el-card>

        <!-- AI聊天卡片 -->
        <el-card class="chat-card">
          <template #header>
            <div class="card-header">
              <span>💬 AI运营助手</span>
              <div class="header-actions">
                <el-tag :type="aiAvailable ? 'success' : 'warning'" size="small">
                  {{ aiAvailable ? '通义千问已连接' : '本地模式' }}
                </el-tag>
                <el-button 
                  type="warning" 
                  size="small" 
                  @click="clearChatHistory"
                  v-if="chatMessages.length > 0"
                >
                  清空历史
                </el-button>
              </div>
            </div>
          </template>

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
                size="small"
              >
                /{{ key }}
              </el-tag>
            </div>
          </div>

          <!-- 聊天区域 -->
          <div class="chat-messages" ref="messagesContainer">
            <div 
              v-for="(message, index) in chatMessages" 
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
            
            <!-- 加载指示器 -->
            <div v-if="chatLoading" class="message assistant">
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
              v-model="chatInput"
              type="textarea"
              :rows="1"
              placeholder="输入您的问题，或点击上方快捷命令..."
              @keydown.ctrl.enter="sendMessage"
              :disabled="chatLoading"
            />
            <div class="input-actions">
              <span class="input-hint">Ctrl+Enter 发送</span>
              <el-button 
                type="primary" 
                size="small" 
                @click="sendMessage"
                :loading="chatLoading"
                :disabled="!chatInput.trim()"
              >
                发送
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Picture, Link, Download } from '@element-plus/icons-vue'
import { analysisApi, type ArticleData } from '../api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const generatingAI = ref(false)
const exporting = ref(false)
const article = ref<ArticleData | null>(null)
const aiAvailable = ref(false)

// AI聊天相关状态
const chatMessages = ref<Array<{role: string, content: string, timestamp: Date}>>([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatSessionId = ref('')
const quickCommands = ref<Record<string, string>>({})
const messagesContainer = ref<HTMLElement | null>(null)

const parsedImages = computed(() => {
  if (!article.value?.imagesInfo) return []
  try {
    const imageUrls = JSON.parse(article.value.imagesInfo)
    // 如果是字符串数组，转换为对象数组
    if (Array.isArray(imageUrls) && imageUrls.length > 0 && typeof imageUrls[0] === 'string') {
      return imageUrls.map((url: string, index: number) => ({
        url: url,
        description: `图片${index + 1}`,
        type: '内容图'
      }))
    }
    return imageUrls
  } catch {
    return []
  }
})

const interactionRate = computed(() => {
  if (!article.value?.readCount7d || article.value.readCount7d === 0) return '0.00'
  return ((article.value.interactionCount7d || 0) / article.value.readCount7d * 100).toFixed(2)
})

const conversionRate = computed(() => {
  if (!article.value?.readCount7d || article.value.readCount7d === 0) return '0.00'
  return ((article.value.productVisit7d || 0) / article.value.readCount7d * 100).toFixed(2)
})

onMounted(async () => {
  const id = route.params.id as string
  if (id) {
    await loadArticle(parseInt(id))
    await checkAIStatus()
    await initializeChat()
    // 如果没有内容，提示用户点击爬取
    if (!article.value?.content && !article.value?.imagesInfo) {
      // 不自动爬取，由用户手动触发
    }
  }
})

const loadArticle = async (id: number) => {
  loading.value = true
  try {
    article.value = await analysisApi.getArticleById(id)
    
    // 如果内容正在处理中，设置自动刷新
    if (article.value && (
      !article.value.content || 
      article.value.crawlStatus === 'PENDING' ||
      !article.value.optimizationSuggestions
    )) {
      // 3秒后自动刷新一次，查看处理结果
      setTimeout(async () => {
        try {
          const updatedArticle = await analysisApi.getArticleById(id)
          if (updatedArticle) {
            article.value = updatedArticle
          }
        } catch (error) {
          console.log('自动刷新失败:', error)
        }
      }, 3000)
    }
  } catch (error) {
    ElMessage.error('加载文章详情失败')
  } finally {
    loading.value = false
  }
}

const checkAIStatus = async () => {
  try {
    const response = await fetch('/api/enhanced/ai-status')
    const data = await response.json()
    aiAvailable.value = data.available
  } catch {
    aiAvailable.value = false
  }
}

const generateAI = async () => {
  if (!article.value?.id) return
  generatingAI.value = true
  try {
    const response = await fetch(`/api/enhanced/articles/${article.value.id}/generate-ai`, {
      method: 'POST'
    })
    const data = await response.json()
    if (data.success) {
      article.value.aiSuggestions = data.aiSuggestions
      ElMessage.success(data.aiAvailable ? 'AI建议生成成功' : '本地分析建议生成成功')
    } else {
      ElMessage.error(data.error || '生成失败')
    }
  } catch (error) {
    ElMessage.error('生成AI建议失败，请稍后重试')
  } finally {
    generatingAI.value = false
  }
}

const exportAI = async () => {
  if (!article.value?.id) return
  if (!article.value?.aiSuggestions) {
    ElMessage.warning('请先生成AI建议')
    return
  }

  exporting.value = true
  try {
    const response = await fetch(`/api/enhanced/articles/${article.value.id}/export-ai`)

    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.message || '导出失败')
    }

    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    let filename = 'AI建议.docx'
    if (contentDisposition) {
      const matches = contentDisposition.match(/filename\*=UTF-8''(.+)/)
      if (matches) {
        filename = decodeURIComponent(matches[1])
      }
    }

    // 下载文件
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)

    ElMessage.success('导出成功')
  } catch (error: any) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const goBack = () => {
  router.back()
}

const getCrawlStatusText = (status: string | undefined) => {
  switch (status) {
    case 'SUCCESS': return '已获取'
    case 'PENDING': return '获取中'
    case 'PARTIAL': return '部分获取'
    case 'FAILED': return '获取失败'
    default: return '自动获取中'
  }
}

const handleImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

const formatNumber = (num: number | null | undefined) => {
  return num?.toLocaleString() || '0'
}

const formatDate = (dateStr: string | undefined) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getInteractionRateClass = () => {
  const rate = parseFloat(interactionRate.value)
  if (rate >= 8) return 'excellent'
  if (rate >= 5) return 'good'
  if (rate >= 3) return 'normal'
  return 'low'
}

const getScoreClass = (score: number | undefined) => {
  if (!score) return ''
  if (score >= 70) return 'excellent'
  if (score >= 50) return 'good'
  if (score >= 30) return 'normal'
  return 'low'
}

const getAnomalyTagType = (status: string | undefined) => {
  if (status === 'GOOD_ANOMALY') return 'success'
  if (status === 'BAD_ANOMALY') return 'danger'
  return 'info'
}

const getAnomalyStatusText = (status: string | undefined) => {
  if (status === 'GOOD_ANOMALY') return '表现优秀'
  if (status === 'BAD_ANOMALY') return '需要优化'
  return '正常'
}

// AI聊天相关方法
const initializeChat = async () => {
  // 生成会话ID
  chatSessionId.value = `chat_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  
  // 加载快捷命令
  await loadQuickCommands()
  
  // 初始化会话
  if (article.value?.id) {
    await initializeChatSession()
  }
}

const loadQuickCommands = async () => {
  try {
    const response = await fetch('/api/multiplatform/chat/quick-commands')
    if (response.ok) {
      quickCommands.value = await response.json()
    }
  } catch (error) {
    console.error('加载快捷命令失败:', error)
  }
}

const initializeChatSession = async () => {
  try {
    const response = await fetch('/api/multiplatform/chat/initialize', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sessionId: chatSessionId.value,
        articleId: article.value?.id
      })
    })
    
    if (response.ok) {
      const data = await response.json()
      if (data.success && data.response) {
        chatMessages.value = [{
          role: 'assistant',
          content: data.response,
          timestamp: new Date()
        }]
        scrollToBottom()
      }
    }
  } catch (error) {
    console.error('初始化聊天会话失败:', error)
  }
}

const sendMessage = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return
  
  const message = chatInput.value.trim()
  chatInput.value = ''
  
  // 添加用户消息
  chatMessages.value.push({
    role: 'user',
    content: message,
    timestamp: new Date()
  })
  
  scrollToBottom()
  chatLoading.value = true
  
  try {
    const response = await fetch('/api/multiplatform/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: message,
        sessionId: chatSessionId.value
      })
    })
    
    if (response.ok) {
      const data = await response.json()
      if (data.success && data.response) {
        chatMessages.value.push({
          role: 'assistant',
          content: data.response,
          timestamp: new Date()
        })
        scrollToBottom()
      } else {
        ElMessage.error(data.message || '发送消息失败')
      }
    } else {
      ElMessage.error('网络请求失败')
    }
  } catch (error) {
    ElMessage.error('发送消息失败，请稍后重试')
  } finally {
    chatLoading.value = false
  }
}

const sendQuickCommand = (commandKey: string) => {
  const command = quickCommands.value[commandKey]
  if (command) {
    chatInput.value = command
    sendMessage()
  }
}

const clearChatHistory = async () => {
  try {
    const response = await fetch('/api/multiplatform/chat/clear', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sessionId: chatSessionId.value,
        clearHistory: true
      })
    })
    
    if (response.ok) {
      chatMessages.value = []
      ElMessage.success('对话历史已清空')
    }
  } catch (error) {
    ElMessage.error('清空历史失败')
  }
}

const formatMessage = (content: string) => {
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/^# (.*$)/gm, '<h3>$1</h3>')
    .replace(/^## (.*$)/gm, '<h4>$1</h4>')
    .replace(/^- (.*$)/gm, '• $1')
    .replace(/\n/g, '<br>')
}

const formatTime = (timestamp: Date) => {
  return timestamp.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

const scrollToBottom = () => {
  setTimeout(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  }, 100)
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 20px;
}
.top-nav {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
.page-title {
  flex: 1;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.detail-layout {
  display: grid;
  grid-template-columns: 1fr 500px;
  gap: 20px;
  align-items: start;
}
.left-column, .right-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.right-column {
  position: sticky;
  top: 20px;
}
.top-blank-area {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.publish-time {
  color: #9ca3af;
  font-size: 14px;
}
.article-link .link-text {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  text-decoration: none;
  font-size: 14px;
}
.article-link .link-text:hover {
  text-decoration: underline;
}
.content-card, .data-card, .ai-card, .optimization-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}
.image-item {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  background: #f3f4f6;
  aspect-ratio: 1;
}
.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.image-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 12px;
  gap: 8px;
}
.image-placeholder .el-icon {
  font-size: 32px;
}
.image-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
  color: #fff;
  font-size: 12px;
  display: flex;
  justify-content: space-between;
}
.text-content h4 {
  margin: 0 0 12px 0;
  color: #374151;
  font-size: 14px;
}
.content-text {
  background: #f9fafb;
  padding: 16px;
  border-radius: 8px;
  line-height: 1.8;
  color: #4b5563;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
}
.data-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.data-section h4 {
  margin: 0 0 12px 0;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.metric-item {
  text-align: center;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}
.metric-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
}
.metric-label {
  font-size: 12px;
  color: #9ca3af;
}
.rates {
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}
.rate-items {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.rate-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rate-label {
  color: #6b7280;
  font-size: 14px;
}
.rate-value {
  font-size: 18px;
  font-weight: 600;
}
.rate-value.excellent {
  color: #10b981;
}
.rate-value.good {
  color: #3b82f6;
}
.rate-value.normal {
  color: #f59e0b;
}
.rate-value.low {
  color: #ef4444;
}
.ai-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  margin-bottom: 20px;
}
.ai-card :deep(.el-card__header) {
  background: transparent;
  border-bottom: 1px solid rgba(255,255,255,0.2);
  color: #fff;
}
.ai-card :deep(.el-card__body) {
  background: transparent;
}
.ai-content {
  background: rgba(255,255,255,0.1);
  border-radius: 8px;
  padding: 16px;
  max-height: 300px;
  overflow-y: auto;
}
.ai-text {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #fff;
  margin: 0;
}
.ai-actions {
  margin-top: 16px;
  text-align: right;
}
.ai-empty {
  text-align: center;
  padding: 30px 20px;
  color: rgba(255,255,255,0.9);
}
.ai-icon-large {
  font-size: 48px;
  margin-bottom: 12px;
}
.ai-hint {
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 20px;
  color: rgba(255,255,255,0.85);
}
.ai-note {
  margin-top: 12px;
  font-size: 12px;
  color: rgba(255,255,255,0.7);
}
.optimization-content {
  max-height: 300px;
  overflow-y: auto;
}
.optimization-text {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
  margin: 0;
}
@media (max-width: 1200px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  .right-column {
    position: static;
  }
}

/* AI聊天样式 */
.chat-card {
  background: #fff;
}

.quick-commands {
  margin-bottom: 12px;
  padding: 10px;
  background: #f8fafc;
  border-radius: 6px;
}

.commands-title {
  font-size: 11px;
  color: #6b7280;
  margin-bottom: 6px;
}

.commands-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.command-tag {
  cursor: pointer;
  transition: all 0.2s;
  font-size: 11px;
}

.command-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.chat-messages {
  max-height: 300px;
  overflow-y: auto;
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
}

.message {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #409eff;
  color: white;
}

.message.assistant .message-avatar {
  background: #67c23a;
  color: white;
}

.message-content {
  flex: 1;
  max-width: calc(100% - 36px);
}

.message.user .message-content {
  text-align: right;
}

.message-text {
  background: #f1f5f9;
  padding: 8px 12px;
  border-radius: 10px;
  line-height: 1.5;
  font-size: 13px;
  word-wrap: break-word;
}

.message.user .message-text {
  background: #409eff;
  color: white;
}

.message-text :deep(h3) {
  margin: 6px 0 3px 0;
  font-size: 14px;
  font-weight: 600;
}

.message-text :deep(h4) {
  margin: 4px 0 3px 0;
  font-size: 13px;
  font-weight: 600;
}

.message-text :deep(code) {
  background: rgba(0,0,0,0.1);
  padding: 1px 3px;
  border-radius: 2px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.message.user .message-text :deep(code) {
  background: rgba(255,255,255,0.2);
}

.message-time {
  font-size: 10px;
  color: #9ca3af;
  margin-top: 2px;
}

.message.user .message-time {
  text-align: right;
}

.typing-indicator {
  display: flex;
  gap: 3px;
  padding: 8px 12px;
}

.typing-indicator span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #9ca3af;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.chat-input {
  padding: 12px 0 0 0;
}

.chat-input :deep(.el-textarea__inner) {
  font-size: 13px;
  line-height: 1.4;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.input-hint {
  font-size: 11px;
  color: #9ca3af;
}
</style>