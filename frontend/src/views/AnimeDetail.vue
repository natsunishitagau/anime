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
                <button @click="toggleFavorite" :class="['btn', anime.isFavorited ? 'btn-primary' : 'btn-secondary']">
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
            <h2>用户评论</h2>
            <div class="review-form" v-if="isAuthenticated">
              <h3>发表评论</h3>
              <div class="rating-input">
                <span>评分:</span>
                <select v-model="reviewRating" class="input">
                  <option v-for="n in 10" :key="n" :value="n">{{ n }}分</option>
                </select>
              </div>
              <textarea v-model="reviewComment" class="input review-textarea" placeholder="写下你的评论..."></textarea>
              <button @click="submitReview" class="btn btn-primary">发布评论</button>
            </div>
            <div v-else class="login-prompt">
              <router-link to="/login" class="btn btn-primary">登录后发表评论</router-link>
            </div>

            <div class="reviews-list">
              <div class="review-card" v-for="review in anime.reviews" :key="review.id">
                <div class="review-header">
                  <span class="reviewer-name">{{ review.user?.username || '匿名用户' }}</span>
                  <span class="review-rating">⭐ {{ review.rating }}</span>
                </div>
                <p class="review-comment">{{ review.comment || '' }}</p>
                <span class="review-date">{{ formatDate(review.createdAt) }}</span>
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
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAnimeStore } from '../stores/anime'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const animeStore = useAnimeStore()
const authStore = useAuthStore()

const anime = ref(null)
const loading = ref(true)
const userRating = ref(0)
const reviewRating = ref(5)
const reviewComment = ref('')

const isAuthenticated = computed(() => authStore.isAuthenticated)

const fetchAnimeDetail = async () => {
  loading.value = true
  try {
    anime.value = await animeStore.fetchAnimeById(route.params.id)
    if (anime.value && anime.value.userRating) {
      userRating.value = anime.value.userRating
    }
  } finally {
    loading.value = false
  }
}

const toggleFavorite = async () => {
  if (!isAuthenticated.value) {
    return
  }
  await animeStore.toggleFavorite(route.params.id)
  anime.value.isFavorited = !anime.value.isFavorited
}

const submitRating = async () => {
  if (!isAuthenticated.value || userRating.value === 0) return
  await animeStore.rateAnime(route.params.id, userRating.value)
}

const submitReview = async () => {
  if (!isAuthenticated.value) return
  await animeStore.addReview(route.params.id, reviewRating.value, reviewComment.value)
  reviewComment.value = ''
  fetchAnimeDetail()
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN')
}

onMounted(() => {
  fetchAnimeDetail()
})
</script>

<style scoped>
.hero-banner {
  position: relative;
  padding: 4rem 0;
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
  padding: 1rem;
  background: var(--background-light);
  border-radius: 0.75rem;
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
}
</style>
