# 多平台数据采集与AI分析系统使用指南

## 系统概述

本系统是一个完整的社交媒体数据采集与AI分析工具，支持：

- **多平台数据采集**：智能识别得物和小红书平台，自动选择对应爬虫
- **AI智能分析**：使用OpenAI GPT模型进行深度数据分析
- **AI聊天互动**：支持多轮对话，针对数据进行深入问答交流
- **智能平台识别**：根据Excel中的"素材来源"字段和链接URL自动识别平台

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                多平台数据采集与AI分析系统                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  Excel数据   │───▶│ 平台识别器  │───▶│  爬虫工厂   │     │
│  │   上传器     │    │DataSource   │    │CrawlerFact  │     │
│  └─────────────┘    └─────────────┘    └──────┬──────┘     │
│                                               │             │
│                            ┌──────────────────┴──────────┐  │
│                            ▼                             ▼  │
│                     ┌─────────────┐           ┌─────────────┐│
│                     │  得物爬虫    │           │ 小红书爬虫  ││
│                     │ DewuCrawler │           │ XHSCrawler  ││
│                     └─────────────┘           └─────────────┘│
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  AI服务管理器                        │   │
│  │  ┌─────────────┐            ┌─────────────┐         │   │
│  │  │   OpenAI    │  ◀──────── │  AI聊天服务  │         │   │
│  │  │   Service   │            │ AIChatSvc   │         │   │
│  │  └─────────────┘            └─────────────┘         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 平台识别逻辑

系统通过以下规则智能识别数据来源：

| 素材来源值 | 识别为 | 使用爬虫 |
|-----------|--------|----------|
| 新媒体图文 | 得物 | DewuCrawler |
| 得物 | 得物 | DewuCrawler |
| dewu | 得物 | DewuCrawler |
| 小红书 | 小红书 | XiaohongshuCrawler |
| xiaohongshu | 小红书 | XiaohongshuCrawler |
| xhs | 小红书 | XiaohongshuCrawler |

## 环境配置

### 1. 后端配置

#### OpenAI API配置
```properties
# application.properties
ai.api.enabled=true
ai.api.key=${OPENAI_API_KEY:your-api-key-here}
ai.api.model=gpt-4o-mini
ai.api.max-tokens=2000
ai.api.temperature=0.7
```

#### 环境变量设置
```bash
# Windows
set OPENAI_API_KEY=sk-your-openai-api-key-here

# Linux/Mac
export OPENAI_API_KEY="sk-your-openai-api-key-here"
```

### 2. 数据库配置
系统使用H2内存数据库，无需额外配置。数据文件存储在 `./data/analysis_db.mv.db`

## 使用方法

### 1. 启动系统

#### 后端启动
```bash
cd BackEnd
./mvnw spring-boot:run
```

#### 前端启动
```bash
cd FrontEnd
npm install
npm run dev
```

### 2. 数据上传与采集

1. **上传Excel文件**
   - 访问 http://localhost:5173
   - 上传包含文章数据的Excel文件
   - 系统自动解析并识别平台

2. **批量数据采集**
   - 进入"数据分析"页面
   - 选择要采集的文章
   - 点击"批量爬取"按钮
   - 系统自动识别平台并使用对应爬虫

3. **单篇重新采集**
   - 在文章详情页点击"重新爬取"
   - 系统重新获取最新数据

### 3. AI分析功能

#### 单篇文章分析
```bash
POST /api/enhanced/articles/{id}/generate-ai
```

#### AI聊天互动
1. 访问"🤖 AI助手"页面
2. 系统自动加载数据并生成初始分析
3. 使用快捷命令或自由对话

#### 快捷命令列表
- `/内容策略` - 获取内容策略建议
- `/发布时间` - 获取最佳发布时间建议
- `/互动提升` - 获取互动提升建议
- `/转化优化` - 获取转化优化建议
- `/平台差异` - 了解平台运营差异
- `/数据分析` - 请求数据分析报告
- `/标题优化` - 标题优化建议
- `/图片建议` - 图片优化建议

## API接口文档

### 多平台数据采集接口

#### 1. 获取平台统计
```http
GET /api/multiplatform/statistics
```

响应：
```json
{
  "totalArticles": 100,
  "platformDistribution": {
    "得物": 60,
    "小红书": 40
  },
  "crawlStatusDistribution": {
    "SUCCESS": 80,
    "FAILED": 15,
    "PENDING": 5
  },
  "supportedPlatforms": {
    "DEWU": "得物",
    "XIAOHONGSHU": "小红书"
  }
}
```

#### 2. 批量爬取数据
```http
POST /api/multiplatform/crawl-batch
Content-Type: application/json

{
  "articleIds": [1, 2, 3, 4, 5]
}
```

#### 3. 重新爬取单篇
```http
POST /api/multiplatform/articles/{id}/recrawl
```

### AI聊天接口

#### 1. 初始化聊天会话
```http
POST /api/multiplatform/chat/initialize
Content-Type: application/json

{
  "sessionId": "chat_123456"
}
```

#### 2. 发送聊天消息
```http
POST /api/multiplatform/chat
Content-Type: application/json

{
  "message": "如何提升互动率？",
  "sessionId": "chat_123456"
}
```

#### 3. 获取快捷命令
```http
GET /api/multiplatform/chat/quick-commands
```

## Excel数据格式

### 必需字段
| 字段名 | 说明 | 示例 |
|--------|------|------|
| data_id | 唯一标识 | 69476af27349bc7ebf11d980 |
| 素材来源 | 关键字段，用于平台识别 | 新媒体图文 / 小红书 |
| 发文链接 | 帖子链接 | https://m.poizon.com/... |
| 品牌 | 品牌名称 | KAPPA背靠背 |
| 发文时间 | 发布时间 | 2025-12-05 |

### 可选字段
| 字段名 | 说明 |
|--------|------|
| 标题 | 帖子标题 |
| 内容形式 | 图文/视频 |
| 发文类型 | 室内上脚/户外穿搭等 |
| 款式信息 | 产品款式编号 |
| 7天阅读/播放 | 7天内阅读量 |
| 7天互动 | 7天内互动量 |
| 7天好物访问 | 7天好物页访问 |
| 7天好物想要 | 7天好物想要数 |

## 核心功能特性

### 1. 智能平台识别
- 根据"素材来源"字段自动识别
- 支持链接URL识别
- 内容特征词识别
- 未知平台兜底处理

### 2. 多平台爬虫
- **得物爬虫**：支持trendId提取和API调用
- **小红书爬虫**：支持noteId提取和签名生成
- **统一接口**：BaseCrawler抽象类
- **工厂模式**：CrawlerFactory动态创建

### 3. AI智能分析
- **OpenAI集成**：GPT-4o-mini模型
- **本地兜底**：规则分析备用方案
- **多轮对话**：会话历史管理
- **快捷命令**：预设问题模板

### 4. 数据处理优化
- **批量处理**：支持异步批量爬取
- **进度回调**：实时进度反馈
- **错误处理**：详细错误信息记录
- **重试机制**：网络请求重试

## 扩展开发

### 添加新平台爬虫

1. **创建爬虫类**
```java
@Component
public class NewPlatformCrawler extends BaseCrawler {
    @Override
    public String getPlatformName() {
        return "新平台";
    }
    
    @Override
    public ArticleData crawl(ArticleData article) {
        // 实现爬取逻辑
        return article;
    }
}
```

2. **注册到枚举**
```java
public enum DataSource {
    NEW_PLATFORM("new_platform", "新平台");
}
```

3. **更新工厂类**
```java
@Autowired
private NewPlatformCrawler newPlatformCrawler;

public void initializeCrawlers() {
    crawlers.put(DataSource.NEW_PLATFORM, newPlatformCrawler);
}
```

### 自定义AI提示词

修改 `application.properties`：
```properties
ai.chat.system-prompt=你是专业的社交媒体分析师，专注于电商内容优化...
```

## 常见问题

### Q: AI服务不可用怎么办？
A: 检查以下配置：
- 是否设置了 `OPENAI_API_KEY` 环境变量
- API密钥是否有效（以sk-开头）
- 网络连接是否正常
- 配置文件中 `ai.api.enabled=true`

### Q: 爬虫返回失败怎么办？
A: 可能原因：
- 目标网站API变更
- 请求频率过高被限制
- 链接格式不正确
- 需要特定的请求头或签名

### Q: 如何支持更多平台？
A: 参考"扩展开发"章节，按照标准流程添加新的爬虫实现。

### Q: 聊天历史如何管理？
A: 系统自动管理会话历史：
- 每个会话最多保留20条历史消息
- 支持手动清空历史
- 会话ID用于区分不同用户

## 性能优化建议

1. **爬虫优化**
   - 合理设置请求间隔（0.5-1.5秒）
   - 使用连接池复用HTTP连接
   - 实现请求缓存机制

2. **AI服务优化**
   - 控制token使用量（max_tokens=2000）
   - 优化提示词长度
   - 实现响应缓存

3. **数据库优化**
   - 添加必要的索引
   - 定期清理过期数据
   - 考虑使用MySQL替代H2

## 版本历史

- **v2.0** - 多平台支持、AI聊天互动、智能平台识别
- **v1.0** - 基础数据分析和异常检测功能

## 许可证

MIT License