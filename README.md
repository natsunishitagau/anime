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
- **Framework**: LangChain + LangGraph
- **Web Framework**: FastAPI
- **LLM Integration**: Qwen (DashScope), LangChain agent
- **Vector Database**: Qdrant (local mode, BGE-large-zh embedding)
- **Reranker**: BGE-reranker-v2-m3 (CrossEncoder)
- **Hybrid Search**: vector + keyword (client-side title index) → RRF fusion → rerank
- **Checkpoint**: PostgreSQL (for conversation history)
- **Search Engine**: SearxNG (meta search, fallback for RAG misses)
- **Eval**: Hit@k / MRR / NDCG benchmark suite, 60-QA test set, auto-generated reports

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
│   │   │   ├── anime_master.py         # LangGraph agent + Agentic RAG (3-tier routing)
│   │   │   └── context_manager.py      # 话题检测 + 多轮上下文管理
│   │   ├── api/                        # FastAPI 接口
│   │   │   ├── main.py                 # API 入口
│   │   │   └── routes/
│   │   │       └── chat.py             # 聊天接口 (流式 SSE)
│   │   ├── db/                         # 数据库相关
│   │   │   ├── qdrant_client.py        # Qdrant 客户端 (local mode)
│   │   │   ├── rag.py                  # Hybrid RAG: 向量+关键词→RRF→Rerank
│   │   │   ├── build_qdrant_vector_db.py # 向量库构建脚本
│   │   │   └── user_chats.py           # 用户对话管理 (PostgreSQL)
│   │   ├── models/                     # 模型相关
│   │   │   ├── chat.py                 # LLM 模型配置 (Qwen via DashScope)
│   │   │   └── anime_processor.py      # 动漫数据处理
│   │   └── utils/                      # 工具函数
│   │       └── snowflake.py            # Snowflake ID 生成
│   ├── prompts/                        # 提示词模板
│   │   └── synopsis.md                 # 剧情简介提示词
│   ├── eval/                           # RAG 评测套件
│   │   ├── eval_dataset.json           # 60 条 QA 评测集 (6 类)
│   │   ├── eval_rag.py                 # 评测引擎 (Hit@k/MRR/NDCG)
│   │   ├── test_e2e.py                 # 端到端 Agent 路由测试
│   │   └── reports/                    # 评测报告 (JSON)
│   ├── scripts/                        # 工具脚本
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

Anime Master 是基于 LangGraph 构建的动漫智能问答助手。核心设计围绕 **Agentic RAG** 展开 — Agent 根据问题类型在「LLM 自身知识 → RAG 检索 → Web 搜索」三层之间智能路由，结合话题感知的上下文管理实现多轮对话消解指代。

### Core Features

- **Agentic RAG**: 三层自适应路由 — 热门作品剧情→LLM 自身知识（0次工具调用），作品匹配/简介核实→RAG 向量库，冷门/时效→Web 搜索
- **Hybrid Search RAG**: 向量（BGE-large-zh, 1024d） + 关键词（客户侧 title 索引）→ RRF 融合 → CrossEncoder 重排，Hit@1 从 27% 提升至 51.3%
- **Topic-Aware 多轮对话**: 正则提取番剧名，thread_id 隔离缓存，第二轮起注入 `[话题上下文]` SystemMessage，消解"他"/"这个"/"主角"等指代
- **实时搜索**: 集成 SearxNG 元搜索引擎，兜底冷门/时效性查询
- **对话压缩**: 每 10 条消息触发 LLM 滚动摘要，保持上下文窗口可控
- **敏感内容过滤**: 自动过滤违规内容，拒绝回答敏感问题
- **评测体系**: 60 条 QA 评测集（6 类），Hit@k / MRR / NDCG 指标追踪

### Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         Anime Master Agent                        │
├────────────────────────────────────────────────────────────────────┤
│  User Input                                                        │
│    │                                                                │
│    ├─▶ [Layer1] 话题检测 extract_anime_name()                      │
│    │     regex: 《书名》/ "XXX的主角" / "XXX好看吗" / 代词过滤      │
│    │     → 按 thread_id 缓存到 _thread_anime dict                  │
│    │                                                                │
│    ├─▶ [Layer2] 上下文组装 _prepare_model_input()                  │
│    │     ① 持久化摘要 (PostgreSQL, 跨重启)                         │
│    │     ② 最近 6 条历史消息                                       │
│    │     ③ [话题上下文] SystemMessage（当前在聊《XXX》）           │
│    │     ④ 当前用户消息                                            │
│    │                                                                │
│    └─▶ [Layer3] LangGraph Agent (qwen3.6-flash)                    │
│          │                                                          │
│          ├─ 热门作品(海贼/火影/鬼灭/芙莉莲等)剧情/设定             │
│          │  → LLM 自身知识，不调工具                                │
│          │                                                          │
│          ├─ 作品匹配/简介/相似作品                                 │
│          │  → rag_search 工具 (Hybrid RAG)                          │
│          │    ┌──────────────────────────────────┐                 │
│          │    │  Qdrant (向量 COSINE 1024d)       │                 │
│          │    │  + 客户侧 title 关键词检索        │                 │
│          │    │  → RRF 融合 → CrossEncoder rerank │                 │
│          │    └──────────────────────────────────┘                 │
│          │                                                          │
│          ├─ 冷门/时效 (新番/声优/2025年XXX)                        │
│          │  → search 工具 (SearxNG)                                │
│          │                                                          │
│          └─ 违规/无关 → 直接拒绝，不调工具                         │
│                                                                     │
│  答覆 → 流式SSE → AgentChat.vue (打字机效果)                      │
└────────────────────────────────────────────────────────────────────┘
```

### RAG Pipeline 细览

```
User Query "航海王"
  │
  ├─▶ Vector Search (Qdrant, COSINE)
  │    "航海王" embedding → top-20 by COSINE score
  │
  ├─▶ Keyword Search (client-side title index)
  │    "航海王" in title → title降序 (精确匹配+Jaccard token overlap)
  │
  ├─▶ RRF Fusion (k=60)
  │    score(anime) = Σ 1/(k + rank_vector) + Σ 1/(k + rank_keyword)
  │
  └─▶ CrossEncoder Rerank (BGE-reranker-v2-m3)
       pair = (query, f"{title} {synopsis}") → relevance score → top-5
```

### Evaluation Benchmark

| 类别 | 说明 | 数量 | Baseline Hit@1 | Hybrid Hit@1 |
|------|------|------|:-:|:-:|
| synopsis_match | 简介直接匹配 | 10 | 30.0% | **60.0%** |
| alias_variant | 译名变体 | 10 | 30.0% | 40.0% |
| detail_qa | 剧情细节 | 10 | 30.0% | **60.0%** |
| multi_hop | 多跳推理 | 6 | 16.7% | 50.0% |
| cold_start | 冷门知识 | 10 | — | — |
| negative | 负面测试 | 10 | — | — |
| **Overall** | | **37 有效** | **27.0%** | **51.3%** |

运行评测：
```bash
cd anime-agent
.venv/Scripts/python.exe eval/eval_rag.py                  # 全量评测
.venv/Scripts/python.exe eval/eval_rag.py --stage vector    # 仅向量检索
.venv/Scripts/python.exe eval/eval_rag.py --category alias  # 单类别
.venv/Scripts/python.exe eval/test_e2e.py                   # Agent 端到端测试
```

### Frontend Integration

前端通过 `AgentChat.vue` 组件实现与 AI Agent 的交互：

**主要功能**:
- 对话列表管理（thread_id 隔离）
- 流式 SSE 消息显示（打字机效果）
- 工具调用状态展示（"思考中"/"调用搜索工具"）

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
| `.venv/Scripts/python.exe eval/eval_rag.py` | 运行 RAG 检索评测（Hit@k/MRR/NDCG） |
| `.venv/Scripts/python.exe eval/eval_rag.py --stage vector` | 仅向量检索阶段评测 |
| `.venv/Scripts/python.exe eval/eval_rag.py --category alias_variant` | 指定类别评测 |
| `.venv/Scripts/python.exe eval/test_e2e.py` | 运行 Agent 端到端测试 |

## Others

本项目是vibe coding的项目，使用的是trae，代码仅供参考
各动漫版权原因本项目仅供学习交流使用，不涉及任何商业用途
本项目不提供视频资源，视频页面功能需指定播放路径
动漫、人物数据来源于https://jikan.moe/、https://bangumi.github.io/api/
searXNG参考【SearXNG：你和 AI 都需要的免费搜索引擎】 https://www.bilibili.com/video/BV1Nqd6YKEhR/?share_source=copy_web&vd_source=6bd506170c10aff71deffb21d48b10be