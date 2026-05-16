<template>
  <div class="favorites-container">
    <div class="page-header">
      <h1>我的收藏</h1>
      <p>这里是你收藏的所有动漫</p>
    </div>

    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="error" class="error">
      <p>{{ error }}</p>
    </div>

    <div v-else-if="favorites.length === 0" class="empty-state">
      <div class="empty-icon">❤️</div>
      <h2>还没有收藏任何动漫</h2>
      <p>去浏览页看看有什么感兴趣的吧！</p>
      <router-link to="/browse" class="btn btn-primary">去浏览</router-link>
    </div>

    <div v-else class="anime-grid">
      <div
        v-for="anime in favorites"
        :key="anime.id"
        class="anime-card"
        @click="viewAnime(anime.id)"
      >
        <div class="anime-poster">
          <img :src="anime.imageUrl" :alt="anime.title" />
          <div class="anime-score">{{ anime.score }}</div>
          <button
            class="remove-favorite-btn"
            @click.stop="removeFromFavorites(anime.id)"
          >
            <span>取消收藏</span>
          </button>
        </div>
        <div class="anime-info">
          <h3>{{ anime.title }}</h3>
          <p class="anime-title-jp">{{ anime.titleJp }}</p>
          <div class="anime-meta">
            <span>{{ anime.type }}</span>
            <span>{{ anime.episodes }}集</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '../utils/axios'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

const loading = ref(false)
const error = ref(null)
const favorites = ref([])

const fetchFavorites = async () => {
  loading.value = true
  error.value = null
  
  try {
    const response = await axios.get('/user/favorites')
    if (response.data && response.data.data) {
      favorites.value = response.data.data
    } else {
      favorites.value = []
    }
  } catch (err) {
    error.value = '获取收藏列表失败'
    console.error('Failed to fetch favorites:', err)
  } finally {
    loading.value = false
  }
}

const removeFromFavorites = async (animeId) => {
  try {
    await axios.delete(`/user/favorites/${animeId}`)
    favorites.value = favorites.value.filter(a => a.id !== animeId)
  } catch (err) {
    console.error('Failed to remove favorite:', err)
  }
}

const viewAnime = (animeId) => {
  window.location.href = `/anime/${animeId}`
}

onMounted(() => {
  if (authStore.isAuthenticated) {
    fetchFavorites()
  }
})
</script>

<style scoped>
.favorites-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}

.page-header {
  text-align: center;
  margin-bottom: 2rem;
}

.page-header h1 {
  font-size: 2.5rem;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.page-header p {
  color: var(--text-secondary);
}

.loading {
  text-align: center;
  padding: 4rem;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 1rem;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error {
  text-align: center;
  color: var(--error-color);
  padding: 4rem;
}

.empty-state {
  text-align: center;
  padding: 6rem 2rem;
  background: var(--background-light);
  border-radius: 12px;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.empty-state h2 {
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.empty-state p {
  color: var(--text-secondary);
  margin-bottom: 1.5rem;
}

.anime-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1.5rem;
}

.anime-card {
  background: var(--background-light);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.anime-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.anime-poster {
  position: relative;
  aspect-ratio: 3/4;
  overflow: hidden;
}

.anime-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.anime-score {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.8);
  color: #ffd700;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: bold;
}

.remove-favorite-btn {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  border: none;
  padding: 12px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  font-size: 0.875rem;
}

.anime-card:hover .remove-favorite-btn {
  opacity: 1;
}

.remove-favorite-btn:hover {
  background: rgba(220, 53, 69, 0.9);
}

.anime-info {
  padding: 12px;
}

.anime-info h3 {
  color: var(--text-primary);
  font-size: 1rem;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.anime-title-jp {
  color: var(--text-muted);
  font-size: 0.875rem;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.anime-meta {
  display: flex;
  gap: 8px;
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.anime-meta span {
  background: var(--border-color);
  padding: 2px 8px;
  border-radius: 4px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20px;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-primary {
  background: var(--primary-color);
  color: #fff;
}

.btn-primary:hover {
  background: var(--primary-hover);
}
</style>