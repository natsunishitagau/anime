# Anime Website Platform

一个基于 Spring Boot + Vue.js 构建的动漫视频网站平台，提供弹幕互动、视频播放、用户管理、消息通知等核心功能。

## Key Features

- 🎬 **视频播放** - 支持动漫视频在线播放，带进度控制和弹幕显示
- 💬 **实时弹幕** - WebSocket 实时弹幕发送与接收，支持敏感词过滤
- 👤 **用户系统** - 用户注册、登录、个人资料管理
- ❤️ **收藏系统** - 动漫收藏夹管理，支持自定义文件夹
- 📺 **观看历史** - 记录用户观看记录，支持续播功能
- 💌 **消息中心** - 系统通知、评论回复、点赞提醒等消息管理
- 🎮 **互动游戏** - 角色动漫评分游戏
- 📊 **数据统计** - 视频播放统计、用户行为分析
- 🔄 **数据同步** - 支持动漫数据定期同步更新
- 🤖 **智能助手** - Anime Master 动漫智能问答助手，基于 LangChain 实现

## Tech Stack

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.2+
- **Database**: PostgreSQL 16+
- **ORM**: Spring Data JPA
- **Authentication**: JWT Token
- **Real-time**: WebSocket (STOMP)
- **Message Queue**: RabbitMQ
- **Caching**: Redis
- **Security**: Spring Security

### Frontend
- **Framework**: Vue 3 + Composition API
- **Build Tool**: Vite
- **UI**: Element Plus
- **Routing**: Vue Router
- **HTTP Client**: Axios
- **WebSocket**: Native WebSocket API

### AI Agent (anime-agent)
- **Language**: Python 3.11+
- **Framework**: LangChain
- **Web Framework**: FastAPI
- **LLM Integration**: OpenAI API
- **Vector Database**: Qdrant (optional, for RAG)
- **Checkpoint**: PostgreSQL (for conversation history)
- **Search Engine**: SearxNG (meta search)

## Project Structure

```
├── backend/                              # Spring Boot 后端
│   ├── src/main/java/com/anime/
│   │   ├── controller/                  # REST API 控制器
│   │   │   ├── AuthController.java      # 认证相关接口
│   │   │   ├── AnimeController.java     # 动漫数据接口
│   │   │   ├── DanmakuController.java   # 弹幕接口
│   │   │   ├── UserController.java      # 用户管理接口
│   │   │   ├── MessageController.java   # 消息接口
│   │   │   └── StatisticsController.java# 统计接口
│   │   ├── service/                     # 业务逻辑层
│   │   │   ├── UserService.java
│   │   │   ├── DanmakuService.java
│   │   │   ├── RecommendationService.java
│   │   │   └── ...
│   │   ├── repository/                  # 数据访问层
│   │   ├── entity/                      # JPA 实体
│   │   ├── dto/                         # 数据传输对象
│   │   ├── config/                      # 配置类
│   │   │   ├── SecurityConfig.java      # 安全配置
│   │   │   ├── WebSocketConfig.java     # WebSocket 配置
│   │   │   └── RedisConfig.java         # Redis 配置
│   │   ├── security/                    # 安全组件
│   │   │   ├── JwtUtils.java            # JWT 工具类
│   │   │   └── JwtAuthenticationFilter.java
│   │   ├── websocket/                   # WebSocket 处理器
│   │   │   └── DanmakuWebSocketHandler.java
│   │   ├── util/                        # 工具类
│   │   │   └── SensitiveWordFilter.java # 敏感词过滤器
│   │   └── AnimeWebsiteApplication.java # 启动类
│   ├── src/main/resources/
│   │   ├── application.yml              # 应用配置
│   │   └── schema.sql                   # 数据库初始化脚本
│   └── pom.xml                          # Maven 依赖

├── frontend/                            # Vue.js 前端
│   ├── src/
│   │   ├── views/                       # 页面视图
│   │   │   ├── Home.vue                 # 首页
│   │   │   ├── Browse.vue               # 浏览页
│   │   │   ├── AnimeDetail.vue          # 动漫详情页
│   │   │   ├── VideoPlayer.vue          # 视频播放器
│   │   │   ├── Profile.vue              # 用户中心
│   │   │   ├── Favorites.vue            # 收藏管理
│   │   │   ├── Messages.vue             # 消息中心
│   │   │   ├── AgentChat.vue            # 智能助手聊天页
│   │   │   ├── Login.vue                # 登录页
│   │   │   └── Register.vue             # 注册页
│   │   ├── components/                  # 可复用组件
│   │   │   ├── Navbar.vue               # 导航栏
│   │   │   ├── Footer.vue               # 页脚
│   │   │   ├── AnimeCard.vue            # 动漫卡片
│   │   │   ├── VideoControls.vue        # 视频控制栏
│   │   │   ├── ConfirmDialog.vue        # 确认弹窗
│   │   │   └── Message.vue              # 消息组件
│   │   ├── utils/                       # 工具函数
│   │   │   ├── axios.js                 # HTTP 封装
│   │   │   ├── message.js               # 消息提示
│   │   │   └── eventBus.js              # 事件总线
│   │   ├── router/                      # 路由配置
│   │   ├── App.vue                      # 根组件
│   │   └── main.js                      # 入口文件
│   ├── index.html
│   ├── vite.config.js
│   └── package.json

├── anime-agent/                         # AI 智能助手服务 (Python)
│   ├── app/
│   │   ├── agent/                      # 智能代理核心
│   │   │   ├── anime_master.py         # 动漫助手核心类
│   │   │   └── context_manager.py      # 上下文管理
│   │   ├── api/                        # FastAPI 接口
│   │   │   ├── main.py                 # API 入口
│   │   │   └── routes/
│   │   │       └── chat.py             # 聊天接口路由
│   │   ├── db/                         # 数据库相关
│   │   │   ├── qdrant_client.py        # Qdrant 客户端
│   │   │   ├── rag.py                  # RAG 服务
│   │   │   └── user_chats.py           # 用户对话管理
│   │   ├── models/                     # 模型相关
│   │   │   ├── chat.py                 # LLM 模型配置
│   │   │   └── anime_processor.py      # 动漫数据处理
│   │   └── utils/                      # 工具函数
│   │       └── snowflake.py            # Snowflake ID 生成
│   ├── prompts/                        # 提示词模板
│   │   └── synopsis.md                 # 剧情简介提示词
│   ├── main.py                         # 服务启动入口
│   └── pyproject.toml                  # Python 项目配置
└── README.md
```

## Prerequisites

### Required
- JDK 21 or higher
- PostgreSQL 16 or higher
- Redis 7 or higher
- Node.js 20 or higher
- Python 3.11 or higher (for anime-agent)
- RabbitMQ 3.12 or higher (optional, for message queue)
- SearxNG (optional, for AI search)

### Optional
- Docker & Docker Compose (for easy environment setup)
- Qdrant (optional, for RAG vector database)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/anime-website.git
cd anime-website
```

### 2. Backend Setup

#### 2.1 Database Configuration

创建 Mysql、PostgreSQL 数据库

PostgreSQL通过user_chats表存储用户对话记录


#### 2.2 Environment Variables

复制并修改 `backend/src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/anime_db
    username: anime_user
    password: anime_password
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

jwt:
  secret: your-256-bit-secret-key-here
  expiration: 86400000

cors:
  allowed-origins: http://localhost:5173
```

#### 2.3 Run Backend

```bash
cd backend
mvn spring-boot:run
```

### 3. Frontend Setup

#### 3.1 Install Dependencies

```bash
cd frontend
npm install
```

#### 3.2 Environment Configuration

创建 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_BASE_URL=ws://localhost:8080/ws
```

#### 3.3 Run Frontend

```bash
npm run dev
```

### 4. AI Agent Setup (anime-agent)

#### 4.1 Install Dependencies

```bash
cd anime-agent
pip install uv
uv sync
```

#### 4.2 Environment Configuration

创建 `.env` 文件：

```env
# OpenAI API Configuration
OPENAI_API_KEY=your-openai-api-key
OPENAI_API_BASE=https://api.openai.com/v1

# PostgreSQL Configuration for Checkpoint
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-password
POSTGRES_DB=anime_questions

# SearxNG Configuration
SEARX_HOST=http://localhost:4000
```


访问 http://localhost:8000 查看 AI 服务状态。

## AI Agent (Anime Master)

### Overview

Anime Master 是基于 LangChain 构建的动漫智能问答助手，为用户提供专业的动漫相关问答服务。

### Core Features

- **智能问答**: 回答用户关于动漫、角色、剧情设定的问题
- **实时搜索**: 集成 SearxNG 元搜索引擎，获取最新动漫资讯
- **RAG 支持**: 支持从向量数据库检索动漫知识库（可选）
- **多轮对话**: 支持上下文保持的多轮对话
- **敏感内容过滤**: 自动过滤违规内容，拒绝回答敏感问题

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Anime Master Agent                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐                   │
│  │   User Input    │───▶│   Prompt Build  │                   │
│  └─────────────────┘    └────────┬────────┘                   │
│                                  ▼                             │
│  ┌─────────────────────────────────────────────┐               │
│  │              LangChain Agent                │               │
│  │  ┌─────────────────────────────────────┐   │               │
│  │  │  System Prompt + Tools + Checkpoint │   │               │
│  │  └─────────────────────────────────────┘   │               │
│  └─────────────────────────────────────────────┘               │
│                          │                                     │
│          ┌───────────────┼───────────────┐                     │
│          ▼               ▼               ▼                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │  SearxNG    │  │   Qdrant    │  │ PostgreSQL  │            │
│  │   Search    │  │   RAG DB    │  │ Checkpoint  │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

### Workflow

1. **用户输入**: 用户在前端聊天界面输入问题
2. **上下文准备**: 从 PostgreSQL Checkpoint 获取历史对话，进行上下文压缩
3. **工具调用**: Agent 根据问题决定是否调用搜索工具或 RAG 工具
4. **LLM 响应**: 结合工具返回结果，生成最终回答
5. **流式输出**: 将响应流式返回前端

### System Prompt

Anime Master 使用精心设计的系统提示词，确保回答：
- 简洁准确，给出明确结论
- 标注信息来源
- 不编造番剧名称、角色、年份
- 拒绝回答与动漫无关的问题
- 拒绝回答违规内容

### Frontend Integration

前端通过 `AgentChat.vue` 组件实现与 AI Agent 的交互：

**主要功能**:
- 对话列表管理
- 流式消息显示（打字机效果）
- 思考状态展示（工具调用时显示"思考中"）

**连接配置**:
```javascript
const AGENT_API_BASE = 'http://localhost:8000'
```

## Security

### JWT Authentication

1. 用户登录成功后获取 JWT Token
2. 后续请求在 `Authorization` Header 中携带 Token：
   ```
   Authorization: Bearer <token>
   ```

### Sensitive Word Filter

弹幕内容会经过敏感词过滤，包含敏感词的弹幕将被拒绝发送。

敏感词列表包括：
- 辱骂性词汇：傻逼、蠢货、废物等
- 色情、赌博、毒品相关词汇
- 政治敏感词汇

## Available Scripts

### Backend (Maven)

| Command | Description |
|---------|-------------|
| `mvn spring-boot:run` | 启动开发服务器 |
| `mvn clean package` | 打包应用 |
| `mvn test` | 运行测试 |

### Frontend (npm)

| Command | Description |
|---------|-------------|
| `npm run dev` | 启动开发服务器 |
| `npm run build` | 构建生产版本 |
| `npm run preview` | 预览生产版本 |
| `npm run lint` | 代码检查 |

### AI Agent (Python)

| Command | Description |
|---------|-------------|
| `uv sync` | 安装依赖 |
| `python main.py` | 启动 AI Agent 服务 |

## Others

本项目是vibe coding的项目，使用的是trae，代码仅供参考
各动漫版权原因本项目仅供学习交流使用，不涉及任何商业用途
本项目不提供视频资源，视频页面功能需指定播放路径
动漫、人物数据来源于https://jikan.moe/、https://bangumi.github.io/api/
searXNG参考【SearXNG：你和 AI 都需要的免费搜索引擎】 https://www.bilibili.com/video/BV1Nqd6YKEhR/?share_source=copy_web&vd_source=6bd506170c10aff71deffb21d48b10be