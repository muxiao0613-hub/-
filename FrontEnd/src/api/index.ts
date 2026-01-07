import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export interface ArticleData {
  id?: number
  dataId: string
  title: string
  brand: string
  publishTime: string
  articleLink: string
  contentType: string
  postType: string
  readCount7d: number
  readCount14d: number
  interactionCount7d: number
  interactionCount14d: number
  shareCount7d: number
  shareCount14d: number
  productVisitCount: number
  anomalyStatus: string
  content?: string
  optimizationSuggestions?: string
  createdAt?: string
  updatedAt?: string
}

export interface Statistics {
  totalCount: number
  normalCount: number
  goodAnomalyCount: number
  badAnomalyCount: number
  avgReadCount: number
  avgInteractionCount: number
}

// API 方法
export const analysisApi = {
  // 上传Excel文件
  uploadExcel: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/analysis/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 获取所有文章
  getAllArticles: (): Promise<ArticleData[]> => {
    return api.get('/analysis/articles')
  },

  // 获取异常文章
  getAnomalousArticles: (): Promise<ArticleData[]> => {
    return api.get('/analysis/articles/anomalous')
  },

  // 根据状态获取文章
  getArticlesByStatus: (status: string): Promise<ArticleData[]> => {
    return api.get(`/analysis/articles/status/${status}`)
  },

  // 获取单个文章
  getArticleById: (id: number): Promise<ArticleData> => {
    return api.get(`/analysis/articles/${id}`)
  },

  // 获取统计信息
  getStatistics: (): Promise<Statistics> => {
    return api.get('/analysis/statistics')
  },

  // 删除所有文章
  deleteAllArticles: () => {
    return api.delete('/analysis/articles')
  }
}

export default api