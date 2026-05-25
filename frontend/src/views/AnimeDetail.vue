<template>
  <div class="anime-detail" v-if="anime">
    <div class="hero-banner">
      <div class="banner-overlay"></div>
      <div class="banner-content">
        <div class="container">
          <div class="anime-header">
            <div class="anime-poster">
              <img :src="anime.anime.imageUrl || 'https://via.placeholder.com/300x450/1e293b/475569?text=No+Image'" :alt="anime.anime.title" />
            </div>
            <div class="anime-info">
              <h1>{{ anime.anime.title }}</h1>
              <p class="title-jp" v-if="anime.anime.titleJp">{{ anime.anime.titleJp }}</p>

              <div class="meta-list">
                <span class="meta-item" v-if="anime.anime.type">{{ anime.anime.type }}</span>
                <span class="meta-item" v-if="anime.anime.episodes">{{ anime.anime.episodes }}话</span>
                <span class="meta-item" v-if="anime.anime.season">{{ anime.anime.season }}</span>
                <span class="meta-item" v-if="anime.anime.year">{{ anime.anime.year }}年</span>
                <span class="meta-item" v-if="anime.anime.status">{{ anime.anime.status }}</span>
              </div>

              <div class="score-display" v-if="anime.anime.score">
                <span class="star">⭐</span>
                <span class="score-value">{{ anime.anime.score.toFixed(1) }}</span>
              </div>

              <div class="genres" v-if="anime.anime.genres && anime.anime.genres.length > 0">
                <span class="genre-tag" v-for="genre in anime.anime.genres" :key="genre.id">
                  {{ genre.name }}
                </span>
              </div>

              <div class="actions">
                <button @click="openFolderDialog" :class="['btn', anime.isFavorited ? 'btn-primary' : 'btn-secondary']">
                  {{ anime.isFavorited ? '❤️ 已收藏' : '🤍 收藏' }}
                </button>
                <div class="rating-section">
                  <span>评分:</span>
                  <select v-model="userRating" @change="submitRating" class="rating-select">
                    <option value="0">选择评分</option>
                    <option v-for="n in 10" :key="n" :value="n">{{ n }}分</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          <h2 class="section-title">剧集列表</h2>
          <div class="episodes-bar" v-if="videos && videos.length > 0">
            <router-link
              v-for="video in videos"
              :key="video.id"
              :to="`/watch/${video.id}`"
              class="episode-badge"
            >
              {{ video.episodeNumber }}
            </router-link>
          </div>
        </div>
      </div>
    </div>

    <div class="container">
      <div class="content-grid">
        <div class="main-content">
          <section class="section">
            <h2>剧情简介</h2>
            <p class="synopsis">{{ anime.anime.synopsis || '暂无简介' }}</p>
          </section>

          <section class="section" v-if="anime.characters && anime.characters.length > 0">
            <h2>角色信息</h2>
            <div class="characters-grid">
              <div class="character-card" v-for="char in anime.characters" :key="char.id">
                <img :src="char.imageUrl || 'https://via.placeholder.com/100x100/1e293b/475569?text=?'" :alt="char.name" />
                <div class="character-info">
                  <h4>{{ char.name }}</h4>
                  <p v-if="char.nameJp">{{ char.nameJp }}</p>
                  <span class="role-badge">{{ char.role }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="section">
            <h2>用户评论 <span v-if="anime.reviews?.length > 0">({{ anime.reviews.length }})</span></h2>
            <div class="review-form" v-if="isAuthenticated">
              <h3>发表评论</h3>
              <textarea v-model="reviewComment" class="input review-textarea" placeholder="写下你的评论..."></textarea>
              <button @click="submitReview" class="btn btn-primary">发布评论</button>
            </div>
            <div v-else class="login-prompt">
              <router-link to="/login" class="btn btn-primary">登录后发表评论</router-link>
            </div>

            <div class="reviews-list">
              <div class="review-card" v-for="review in anime.reviews" :key="review.id">
                <div class="review-avatar">
                  <img :src="getAvatar(review.avatarUrl, review.username)" :alt="review.username || '匿名用户'" class="avatar-img" />
                </div>
                <div class="review-content">
                  <div class="review-header">
                    <span class="reviewer-name">{{ review.username || '匿名用户' }}</span>
                  </div>
                  <p class="review-comment">{{ review.comment || '' }}</p>
                  <span class="review-date">{{ formatDate(review.createdAt) }}</span>
                </div>
              </div>
            </div>
          </section>
        </div>

        <aside class="sidebar">
          <section class="section">
            <h3>基本信息</h3>
            <dl class="info-list">
              <dt>类型</dt>
              <dd>{{ anime.anime.type || '未知' }}</dd>
              <dt>集数</dt>
              <dd>{{ anime.anime.episodes || '未知' }}</dd>
              <dt>季度</dt>
              <dd>{{ anime.anime.season || '未知' }}</dd>
              <dt>年份</dt>
              <dd>{{ anime.anime.year || '未知' }}</dd>
              <dt>状态</dt>
              <dd>{{ anime.anime.status || '未知' }}</dd>
              <dt>制作公司</dt>
              <dd>{{ anime.anime.studios || '未知' }}</dd>
              <dt>原作</dt>
              <dd>{{ anime.anime.source || '未知' }}</dd>
            </dl>
          </section>

          <section class="section" v-if="anime.similarAnime && anime.similarAnime.length > 0">
            <h3>相似推荐</h3>
            <div class="similar-list">
              <router-link v-for="item in anime.similarAnime" :key="item.id" :to="`/anime/${item.id}`" class="similar-item">
                <img :src="item.imageUrl || 'https://via.placeholder.com/80x120/1e293b/475569?text=?'" :alt="item.title" />
                <div class="similar-info">
                  <h4>{{ item.title }}</h4>
                  <span class="score" v-if="item.score">⭐ {{ item.score.toFixed(1) }}</span>
                </div>
              </router-link>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </div>

  <div v-else-if="loading" class="loading">
    <div class="spinner"></div>
  </div>

  <div v-else class="error-state">
    <p>番剧不存在</p>
    <router-link to="/" class="btn btn-primary">返回首页</router-link>
  </div>

  <div v-if="showFolderDialog" class="dialog-overlay" @click="showFolderDialog = false">
    <div class="dialog" @click.stop>
      <h2>选择收藏夹</h2>
      <div v-if="folders.length === 0" class="empty-folders">
        <p>还没有收藏夹，创建一个吧！</p>
        <div class="new-folder-input">
          <input v-model="newFolderName" placeholder="输入收藏夹名称" @keyup.enter="createAndAdd" />
          <button @click="createAndAdd" class="btn btn-primary">创建并添加</button>
        </div>
      </div>
      <div v-else class="folder-list">
        <div
          v-for="folder in folders"
          :key="folder.id"
          class="folder-item"
          @click="addToFolder(folder.id)"
        >
          <span class="folder-name">{{ folder.name }}</span>
          <span class="folder-count">{{ folder.count }} 部</span>
        </div>
        <div class="new-folder-section">
          <div class="new-folder-input">
            <input v-model="newFolderName" placeholder="创建新收藏夹..." />
            <button @click="createAndAdd" class="btn btn-primary" :disabled="!newFolderName.trim()">创建</button>
          </div>
        </div>
      </div>
      <div v-if="dialogError" class="error-message">{{ dialogError }}</div>
      <div class="dialog-actions">
        <button @click="showFolderDialog = false" class="btn btn-secondary">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAnimeStore } from '../stores/anime'
import { useAuthStore } from '../stores/auth'
import axios from '../utils/axios'
import { $message } from '../utils/message'

const route = useRoute()
const animeStore = useAnimeStore()
const authStore = useAuthStore()

const anime = ref(null)
const loading = ref(true)
const userRating = ref(0)
const reviewComment = ref('')
const showFolderDialog = ref(false)
const folders = ref([])
const newFolderName = ref('')
const dialogError = ref('')
const videos = ref([])

const isAuthenticated = computed(() => authStore.isAuthenticated)

const fetchAnimeDetail = async () => {
  loading.value = true
  try {
    anime.value = await animeStore.fetchAnimeById(route.params.id)
    if (anime.value && anime.value.userRating) {
      userRating.value = anime.value.userRating
    }
    await fetchVideos()
  } finally {
    loading.value = false
  }
}

const fetchVideos = async () => {
  try {
    const response = await axios.get(`/videos/anime/${route.params.id}`)
    if (response.data && response.data.data) {
      videos.value = response.data.data
    }
  } catch (err) {
    console.error('Failed to fetch videos:', err)
    videos.value = []
  }
}

const fetchFolders = async () => {
  try {
    const response = await axios.get('/user/favorites/folders')
    if (response.data && response.data.data) {
      folders.value = response.data.data
    }
  } catch (err) {
    console.error('Failed to fetch folders:', err)
  }
}

const openFolderDialog = async () => {
  if (!isAuthenticated.value) {
    return
  }
  dialogError.value = ''
  newFolderName.value = ''
  await fetchFolders()
  showFolderDialog.value = true
}

const addToFolder = async (folderId) => {
  try {
    await axios.post(`/user/favorites/folders/${folderId}/anime/${route.params.id}`)
    showFolderDialog.value = false
    if (anime.value) {
      anime.value.isFavorited = true
    }
    $message.success('已添加到收藏夹')
  } catch (err) {
    dialogError.value = err.response?.data?.message || '添加失败'
  }
}

const createAndAdd = async () => {
  if (!newFolderName.value || !newFolderName.value.trim()) return

  dialogError.value = ''
  try {
    const response = await axios.post('/user/favorites/folders', { name: newFolderName.value.trim() })
    const newFolderId = response.data.data.id
    await addToFolder(newFolderId)
  } catch (err) {
    dialogError.value = err.response?.data?.message || '创建失败'
  }
}

const submitRating = async () => {
  if (!isAuthenticated.value || userRating.value === 0) return
  const success = await animeStore.rateAnime(route.params.id, userRating.value)
  if (success) {
    $message.success('评分成功')
  }
}

const submitReview = async () => {
  if (!isAuthenticated.value) return
  const success = await animeStore.addReview(route.params.id, reviewComment.value)
  if (success) {
    $message.success('评论发布成功')
    reviewComment.value = ''
    fetchAnimeDetail()
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN')
}

const getAvatar = (avatarUrl, username) => {
  if (avatarUrl) return avatarUrl
  return '/src/assets/avatars/default.svg'
}

const formatDuration = (seconds) => {
  if (!seconds || isNaN(seconds)) return ''
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

onMounted(() => {
  fetchAnimeDetail()
})

watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    fetchAnimeDetail()
  }
})

onUnmounted(() => {
  anime.value = null
  loading.value = true
})
</script>

<style scoped>
.hero-banner {
  position: relative;
  padding-top: 4rem;
  background: linear-gradient(180deg, var(--background-light) 0%, var(--background-dark) 100%);
}

.banner-overlay {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 0%, var(--background-dark) 70%);
}

.banner-content {
  position: relative;
  z-index: 1;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.anime-header {
  display: flex;
  gap: 2rem;
}

.anime-poster {
  flex-shrink: 0;
  width: 280px;
  border-radius: 1rem;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.anime-poster img {
  width: 100%;
  display: block;
}

.anime-info {
  flex: 1;
}

.anime-info h1 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.title-jp {
  color: var(--text-secondary);
  margin-bottom: 1rem;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.meta-item {
  padding: 0.25rem 0.75rem;
  background: var(--surface-color);
  border-radius: 0.25rem;
  font-size: 0.875rem;
}

.score-display {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.star {
  font-size: 1.5rem;
}

.score-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #fbbf24;
}

.genres {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.genre-tag {
  padding: 0.5rem 1rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 9999px;
  font-size: 0.875rem;
  font-weight: 500;
}

.actions {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.rating-section {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.rating-select {
  padding: 0.5rem 1rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 0.5rem;
  color: var(--text-primary);
}

.section-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin-top: 1rem;
  padding-bottom: 0.5rem;
}

.episodes-bar {
  display: flex;
  justify-content: start;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--border-color);
}

.episode-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 50px;
  height: 40px;
  padding: 0 0.75rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 0.5rem;
  color: var(--text-primary);
  text-decoration: none;
  font-weight: 500;
  transition: all 0.2s;
}

.episode-badge:hover {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
  transform: translateY(-2px);
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 350px;
  gap: 2rem;
  padding: 2rem 0;
}

.section {
  margin-bottom: 2rem;
}

.section h2, .section h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color);
}

.synopsis {
  line-height: 1.8;
  color: var(--text-secondary);
}

.characters-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.character-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: var(--background-light);
  border-radius: 0.75rem;
}

.character-card img {
  width: 60px;
  height: 60px;
  border-radius: 0.5rem;
  object-fit: cover;
}

.character-info h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.character-info p {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 0.25rem;
}

.role-badge {
  font-size: 0.625rem;
  padding: 0.125rem 0.5rem;
  background: var(--primary-color);
  border-radius: 9999px;
}

.review-form {
  padding: 1.5rem;
  background: var(--background-light);
  border-radius: 1rem;
  margin-bottom: 1.5rem;
}

.review-form h3 {
  border: none;
  margin-bottom: 1rem;
}

.rating-input {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.rating-input select {
  width: auto;
}

.review-textarea {
  width: 100%;
  min-height: 100px;
  resize: vertical;
  margin-bottom: 1rem;
}

.login-prompt {
  padding: 2rem;
  text-align: center;
  background: var(--background-light);
  border-radius: 1rem;
  margin-bottom: 1.5rem;
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.review-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: var(--background-light);
  border-radius: 0.75rem;
}

.review-avatar {
  flex-shrink: 0;
}

.avatar-img {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.review-content {
  flex: 1;
  min-width: 0;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.reviewer-name {
  font-weight: 600;
}

.review-rating {
  color: #fbbf24;
}

.review-comment {
  color: var(--text-secondary);
  margin-bottom: 0.5rem;
}

.review-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.sidebar {
  position: sticky;
  top: 7rem;
  height: fit-content;
}

.info-list {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1rem;
}

.info-list dt {
  color: var(--text-muted);
  font-size: 0.875rem;
}

.info-list dd {
  font-size: 0.875rem;
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.similar-item {
  display: flex;
  gap: 0.75rem;
  padding: 0.5rem;
  background: var(--background-light);
  border-radius: 0.5rem;
  text-decoration: none;
  transition: background 0.2s;
}

.similar-item:hover {
  background: var(--surface-color);
}

.similar-item img {
  width: 50px;
  height: 70px;
  border-radius: 0.25rem;
  object-fit: cover;
}

.similar-info h4 {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
}

.similar-info .score {
  font-size: 0.75rem;
  color: #fbbf24;
}

.loading, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  gap: 1rem;
}

@media (max-width: 992px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .score-display {
    justify-content: center;
  }

  .sidebar {
    position: static;
  }

  .anime-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .anime-poster {
    width: 200px;
  }

  .meta-list {
    justify-content: center;
  }

  .genres {
    justify-content: center;
  }

  .actions {
    justify-content: center;
    flex-wrap: wrap;
  }

  .episodes-bar {
    justify-content: left;
  }
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: linear-gradient(145deg, #1e293b 0%, #0f172a 100%);
  padding: 2rem;
  border-radius: 16px;
  width: 100%;
  max-width: 450px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow:
    0 0 0 1px rgba(99, 102, 241, 0.3),
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 0 40px rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.dialog h2 {
  margin: 0 0 1.5rem 0;
  color: #f1f5f9;
  font-size: 1.35rem;
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.empty-folders {
  text-align: center;
  padding: 1.5rem 0;
}

.empty-folders p {
  color: #94a3b8;
  margin-bottom: 1rem;
}

.folder-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.folder-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.folder-item:hover {
  background: rgba(99, 102, 241, 0.15);
  border-color: rgba(99, 102, 241, 0.4);
  transform: translateX(4px);
}

.folder-name {
  color: #e2e8f0;
  font-weight: 500;
}

.folder-count {
  color: #64748b;
  font-size: 0.875rem;
  background: rgba(51, 65, 85, 0.6);
  padding: 2px 10px;
  border-radius: 12px;
}

.new-folder-section {
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  padding-top: 1.25rem;
  margin-top: 0.75rem;
}

.new-folder-input {
  display: flex;
  gap: 0.75rem;
}

.new-folder-input input {
  flex: 1;
  padding: 0.75rem 1rem;
  border: 1px solid rgba(71, 85, 105, 0.6);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.8);
  color: #e2e8f0;
  font-size: 0.9rem;
}

.new-folder-input input::placeholder {
  color: #64748b;
}

.new-folder-input input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
}

.error-message {
  color: #f87171;
  font-size: 0.875rem;
  margin-top: 1rem;
  text-align: center;
  background: rgba(239, 68, 68, 0.1);
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1.25rem;
  padding-top: 1.25rem;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.875rem;
  transition: background 0.2s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--primary-color);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.btn-secondary {
  background: var(--border-color);
  color: var(--text-primary);
}

.btn-secondary:hover {
  background: var(--background-light);
}
</style>