<template>
  <div id="app">
    <el-container>
      <el-header>
        <div class="header-content">
          <h1>电商流量数据分析系统</h1>
          <nav>
            <router-link to="/" class="nav-link">数据上传</router-link>
            <router-link to="/dashboard" class="nav-link">数据分析</router-link>
            <router-link to="/anomaly" class="nav-link">异常检测</router-link>
            <router-link to="/ai-chat" class="nav-link">🤖 AI助手</router-link>
          </nav>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
// Vue 3 Composition API
</script>

<style>
#app {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
}

.el-header {
  background-color: #409EFF;
  color: white;
  padding: 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 20px;
}

.header-content h1 {
  margin: 0;
  font-size: 24px;
}

nav {
  display: flex;
  gap: 20px;
}

.nav-link {
  color: white;
  text-decoration: none;
  padding: 8px 16px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.nav-link:hover,
.nav-link.router-link-active {
  background-color: rgba(255, 255, 255, 0.2);
}

.el-main {
  padding: 20px;
  min-height: calc(100vh - 60px);
}
</style>