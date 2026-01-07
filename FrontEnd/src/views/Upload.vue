<template>
  <div class="upload-container">
    <el-card class="upload-card">
      <template #header>
        <div class="card-header">
          <span>数据上传</span>
        </div>
      </template>
      
      <div class="upload-content">
        <el-upload
          ref="uploadRef"
          class="upload-demo"
          drag
          :auto-upload="false"
          :on-change="handleFileChange"
          :before-upload="beforeUpload"
          accept=".xlsx,.xls"
          :limit="1"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将Excel文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              只能上传 xlsx/xls 文件，且不超过 50MB
            </div>
          </template>
        </el-upload>

        <div class="upload-actions" v-if="selectedFile">
          <el-button type="primary" @click="uploadFile" :loading="uploading">
            开始分析
          </el-button>
          <el-button @click="clearFile">清除文件</el-button>
        </div>

        <div class="file-info" v-if="selectedFile">
          <h3>文件信息</h3>
          <p><strong>文件名：</strong>{{ selectedFile.name }}</p>
          <p><strong>文件大小：</strong>{{ formatFileSize(selectedFile.size) }}</p>
          <p><strong>文件类型：</strong>{{ selectedFile.type }}</p>
        </div>
      </div>
    </el-card>

    <!-- 上传结果 -->
    <el-card v-if="uploadResult" class="result-card">
      <template #header>
        <div class="card-header">
          <span>分析结果</span>
        </div>
      </template>
      
      <div class="result-content">
        <el-alert
          :title="uploadResult.success ? '分析成功' : '分析失败'"
          :type="uploadResult.success ? 'success' : 'error'"
          :description="uploadResult.message"
          show-icon
          :closable="false"
        />
        
        <div v-if="uploadResult.success" class="result-stats">
          <el-statistic title="总文章数" :value="uploadResult.totalCount" />
          <el-button type="primary" @click="$router.push('/dashboard')">
            查看分析结果
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 使用说明 -->
    <el-card class="help-card">
      <template #header>
        <div class="card-header">
          <span>使用说明</span>
        </div>
      </template>
      
      <div class="help-content">
        <h3>Excel文件格式要求：</h3>
        <ul>
          <li>第一行为标题行，包含以下列：</li>
          <li>data_id（文章ID）、标题、品牌、发文时间、发文链接</li>
          <li>内容形式、发文类型、7天阅读量、14天阅读量</li>
          <li>7天互动量、14天互动量、7天分享量、14天分享量、好物访问量</li>
        </ul>
        
        <h3>分析功能：</h3>
        <ul>
          <li>🔍 流量异常检测：识别表现异常好或异常差的文章</li>
          <li>🕷️ 内容抓取：自动抓取文章链接的内容</li>
          <li>📊 数据分析：分析内容质量与流量的关联</li>
          <li>💡 优化建议：为每篇文章生成个性化优化建议</li>
        </ul>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { analysisApi } from '../api'

const uploadRef = ref()
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadResult = ref<any>(null)

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
  uploadResult.value = null
}

const beforeUpload = (file: File) => {
  const isExcel = file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' || 
                  file.type === 'application/vnd.ms-excel'
  const isLt50M = file.size / 1024 / 1024 < 50

  if (!isExcel) {
    ElMessage.error('只能上传 Excel 文件!')
    return false
  }
  if (!isLt50M) {
    ElMessage.error('文件大小不能超过 50MB!')
    return false
  }
  return false // 阻止自动上传
}

const uploadFile = async () => {
  if (!selectedFile.value) {
    ElMessage.error('请先选择文件')
    return
  }

  uploading.value = true
  try {
    const result = await analysisApi.uploadExcel(selectedFile.value)
    uploadResult.value = result
    ElMessage.success('文件上传和分析成功!')
  } catch (error: any) {
    console.error('Upload error:', error)
    uploadResult.value = {
      success: false,
      message: error.response?.data?.message || '上传失败，请重试'
    }
    ElMessage.error('上传失败: ' + (error.response?.data?.message || error.message))
  } finally {
    uploading.value = false
  }
}

const clearFile = () => {
  selectedFile.value = null
  uploadResult.value = null
  uploadRef.value?.clearFiles()
}

const formatFileSize = (size: number) => {
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 0 auto;
}

.upload-card,
.result-card,
.help-card {
  margin-bottom: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.upload-content {
  text-align: center;
}

.upload-demo {
  margin-bottom: 20px;
}

.upload-actions {
  margin: 20px 0;
}

.upload-actions .el-button {
  margin: 0 10px;
}

.file-info {
  text-align: left;
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  margin-top: 20px;
}

.file-info h3 {
  margin-top: 0;
  color: #409EFF;
}

.result-content {
  text-align: center;
}

.result-stats {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.help-content {
  text-align: left;
}

.help-content h3 {
  color: #409EFF;
  margin-top: 20px;
}

.help-content ul {
  padding-left: 20px;
}

.help-content li {
  margin: 8px 0;
  line-height: 1.5;
}
</style>