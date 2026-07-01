# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Anime video website platform with danmaku (bullet-screen) commenting, built by "vibe coding" with Trae IDE. Spring Boot + Vue 3 + Python AI agent, tri-module monorepo. For learning/exchange only, no commercial use.

## Commands

### Backend (Spring Boot, Java 17+)
```bash
cd backend
mvn spring-boot:run        # Start dev server on :8080
mvn clean package           # Build fat JAR
mvn test                    # Run tests (test directory is currently empty)
```

### Frontend (Vue 3 + Vite)
```bash
cd frontend
npm install                 # Install dependencies
npm run dev                 # Start dev server on :5173 (proxies API to :8080)
npm run build               # Build to dist/
npm run preview             # Preview production build
```

### AI Agent (Python 3.12, FastAPI)
```bash
cd anime-agent
pip install uv && uv sync   # Install dependencies (uses Tsinghua PyPI mirror)
python main.py              # Start on :8000
```

### RAG Evaluation
```bash
cd anime-agent
.venv/Scripts/python.exe eval/eval_rag.py                 # Full eval (retrieval + rerank)
.venv/Scripts/python.exe eval/eval_rag.py --stage vector   # Vector-only eval
.venv/Scripts/python.exe eval/eval_rag.py --category alias # Single-category eval
.venv/Scripts/python.exe eval/eval_rag.py --output report.json  # Custom output
.venv/Scripts/python.exe eval/test_e2e.py                  # End-to-end agent routing test
```
Reports saved to `anime-agent/eval/reports/eval_<timestamp>.json`.

## Architecture (3 Modules)

### 1. Backend — `backend/` (Java, Spring Boot 3.2)
Standard layered architecture: Controller → Service → Repository → Entity. JWT auth via custom filter. WebSocket for real-time danmaku.

- **Config** (`com.anime.config`): SecurityConfig (stateless JWT, CORS for :5173), WebSocketConfig (STOMP), RabbitMQConfig, RedisConfig
- **Controllers** (`com.anime.controller`): AuthController, AnimeController, DanmakuController, UserController, MessageController, AnimeVideoController, WatchHistoryController, StatisticsController, etc.
- **Security**: JwtUtils (jjwt 0.12.3) + JwtAuthenticationFilter, BCrypt passwords
- **WebSocket**: DanmakuWebSocketHandler (per-connection rate limiting via Redis)
- **Persistence**: Spring Data JPA + MySQL (ddl-auto: update), Redis for cache/rate-limit, RabbitMQ for async messaging
- **API patterns**: All responses follow `{ success: boolean, data: any, message: string }` shape. Public endpoints under `/api/auth/**`, `/api/anime/**`, `/api/danmaku/**`, `/api/stats/**`; authenticated under `/api/user/**`, `/api/user-settings/**`
- **Video**: HLS streaming via `/hls/**` endpoint

### 2. Frontend — `frontend/` (Vue 3, Composition API)
SPA with Vue Router + Pinia stores + Element Plus UI.

- **Router** (`src/router/index.js`): 13 routes, lazy-loaded views. Auth guard redirects to Login. Routes: `/`, `/anime/:id`, `/watch/:id`, `/browse`, `/login`, `/register`, `/profile`, `/favorites`, `/messages`, `/search`, `/game`, `/game/anime`, `/game/character`, `/agent`
- **Stores** (`src/stores/`): `auth.js` (login/register/verify/logout, token in localStorage), `anime.js` (CRUD, search, filters, favorites, reviews)
- **HTTP**: `src/utils/axios.js` — base `/api`, Bearer token interceptor, 50s timeout, error logging
- **Danmaku**: `src/composables/useDanmakuEngine.js` + `useDanmakuWebSocket.js` — canvas-based rendering + STOMP WebSocket
- **Key components**: AnimeCard, Navbar, Footer, VideoControls, ConfirmDialog, Message
- **Key views**: Home (trending/top), AnimeDetail (info + reviews), VideoPlayer (HLS + danmaku), AgentChat (streaming AI chat)

### 3. AI Agent — `anime-agent/` (Python, FastAPI + LangChain)
Anime Master Q&A agent. FastAPI on :8000, LangGraph agent with tool-use + RAG + topic-aware context.

- **Entry**: `main.py` → uvicorn, `app/api/main.py` → FastAPI app with CORS
- **Agent core**:
  - `app/agent/anime_master.py` — LangGraph agent with **Agentic RAG** (3-tier routing: LLM knowledge → RAG → Web search), two registered tools: `rag_search` (Qdrant) + `search` (SearxNG)
  - `app/agent/context_manager.py` — topic detection via regex (anime name extraction, pronoun blacklist), thread-isolated `_thread_anime` dict, rolling LLM summary compression every 10 messages
- **RAG pipeline**:
  - `app/db/rag.py` — **Hybrid Search**: vector (BGE-large-zh, 1024d) + keyword (client-side title index, 2021 anime) → RRF fusion → BGE-reranker-v2-m3 CrossEncoder rerank
  - `app/db/qdrant_client.py` + `build_qdrant_vector_db.py` — Qdrant local mode
- **Evaluation** (`eval/`):
  - `eval/eval_dataset.json` — 60 QA pairs across 6 categories
  - `eval/eval_rag.py` — Hit@k / MRR / NDCG metrics, stage-aware, per-category breakdown
  - `eval/test_e2e.py` — end-to-end agent routing verification
  - `eval/reports/` — timestamped JSON reports for regression tracking
- **Storage**: PostgreSQL for conversation checkpoints (`app/db/user_chats.py`)
- **Frontend integration**: `AgentChat.vue` calls `http://localhost:8000` directly, streaming response with typewriter effect

## Key Conventions

- **API response format**: Backend controllers return `ApiResponse.success(data)` / `ApiResponse.error(message)` — check `response.data.success` on frontend
- **Auth**: JWT stored in `localStorage('token')`, sent as `Authorization: Bearer <token>`. Token verified on app load via `/api/auth/verify`
- **CORS**: Backend allows `localhost:5173`, `localhost:3000`, `127.0.0.1:5173`
- **Database**: MySQL for business data, PostgreSQL (separate) for AI conversation checkpoints
- **File uploads**: Stored in `backend/uploads/avatars/` and `backend/uploads/videos/`, served under `/uploads/**`
- **Danmaku rate limiting**: Per-connection Redis-based rate limiting in DanmakuWebSocketHandler
- **Sensitive word filter**: Custom DFA-based filter rejects danmaku containing banned words
- **HLS video**: Backend serves HLS segments under `/hls/**`, frontend uses hls.js for playback
- **RAG/Hybrid Search**: `app/db/rag.py` uses `BAAI/bge-large-zh` for embedding + `BAAI/bge-reranker-v2-m3` for reranking. Hybrid Search = vector (Qdrant COSINE) + keyword (client-side title index) → RRF fusion → rerank. Qdrant runs in local mode; payload indexes have no effect.
- **Agentic RAG**: LangGraph agent registers two tools — `rag_search` (Qdrant, first priority) and `search` (SearxNG, fallback). SYSTEM_PROMPT routes queries: hot anime → LLM knowledge (no tools), match/lookup → RAG, real-time/cold → web search.
- **Topic-Aware**: `context_manager.py` detects anime name from user message via regex, caches per thread_id, injects `[话题上下文]` SystemMessage in `_prepare_model_input()` for multi-turn pronoun resolution.
- **Eval**: 60-QA benchmark in `eval/eval_dataset.json` (6 categories). `eval_rag.py` computes Hit@k, MRR, NDCG with per-category breakdown. Baseline: Hit@1=27% → after Hybrid Search: 51.3%.
- **No unit tests**: `backend/src/test/` and frontend test setup are empty. All automated validation lives in `anime-agent/eval/`.
- **PowerShell environment**: Project uses Git Bash (POSIX), but AGENTS.md notes PowerShell compat concerns. Use `;` for chaining if ever in PowerShell

## Other Notes

- Anime data sourced from Jikan API (MyAnimeList) and Bangumi API
- Search uses SearxNG meta search engine (optional, for AI agent)
- RabbitMQ connects to a remote VM in dev config
- Trae IDE skills exist in `.trae/` and `anime-agent/.trae/` but are not relevant to Claude
