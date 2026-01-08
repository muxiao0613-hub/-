import { createRouter, createWebHistory } from 'vue-router'
import Upload from '../views/Upload.vue'
import Dashboard from '../views/Dashboard.vue'
import Anomaly from '../views/Anomaly.vue'
import ArticleDetail from '../views/ArticleDetail.vue'

const routes = [
  {
    path: '/',
    name: 'Upload',
    component: Upload
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/anomaly',
    name: 'Anomaly',
    component: Anomaly
  },
  {
    path: '/article/:id',
    name: 'ArticleDetail',
    component: ArticleDetail
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router