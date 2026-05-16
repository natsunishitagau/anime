<template>
  <div class="profile">
    <div class="container">
      <div class="profile-header">
        <div class="avatar">
          {{ user?.username?.[0]?.toUpperCase() }}
        </div>
        <div class="user-info">
          <h1>{{ user?.username }}</h1>
          <p>{{ user?.email }}</p>
          <p class="member-since" v-if="user?.createdAt">
            加入于 {{ formatDate(user.createdAt) }}
          </p>
        </div>
      </div>

      <div class="profile-content">
        <section class="section">
          <h2>我的收藏</h2>
          <div v-if="favorites.length > 0" class="grid grid-5">
            <AnimeCard v-for="anime in favorites" :key="anime.id" :anime="anime" />
          </div>
          <div v-else class="empty-state">
            <p>还没有收藏任何番剧</p>
            <router-link to="/browse" class="btn btn-primary">去浏览</router-link>
          </div>
        </section>

        <section class="section">
          <h2>观看历史</h2>
          <div v-if="watchHistory.length > 0" class="watch-history-list">
            <div class="history-item" v-for="item in watchHistory" :key="item.anime.id">
              <router-link :to="`/anime/${item.anime.id}`" class="history-anime">
                <img :src="item.anime.imageUrl || 'https://via.placeholder.com/80x120/1e293b/475569?text=?'" :alt="item.anime.title" />
                <div class="history-info">
                  <h4>{{ item.anime.title }}</h4>
                  <p>观看进度: {{ item.progress }}%</p>
                  <span v-if="item.completed" class="badge badge-success">已完成</span>
                </div>
              </router-link>
            </div>
          </div>
          <div v-else class="empty-state">
            <p>还没有观看记录</p>
          </div>
        </section>

        <section class="section">
          <h2>账号设置</h2>
          <div class="settings-card">
            <button @click="handleLogout" class="btn btn-secondary">退出登录</button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AnimeCard from '../components/AnimeCard.vue'

const router = useRouter()
const authStore = useAuthStore()

const user = computed(() => authStore.user)
const favorites = ref([])
const watchHistory = ref([])

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN')
}

const fetchFavorites = async () => {
  try {
    const response = await fetch('/api/user/favorites', {
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })
    const data = await response.json()
    favorites.value = data.data || []
  } catch (error) {
    console.error('Failed to fetch favorites:', error)
  }
}

const fetchWatchHistory = async () => {
  try {
    const response = await fetch('/api/user/watch-history', {
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })
    const data = await response.json()
    watchHistory.value = data.data || []
  } catch (error) {
    console.error('Failed to fetch watch history:', error)
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/')
}

onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.user) {
    await authStore.verifyToken()
  }
  if (authStore.isAuthenticated) {
    fetchFavorites()
    fetchWatchHistory()
  }
})
</script>

<style scoped>
.profile {
  padding: 2rem 0;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 2rem;
  padding: 2rem;
  background: var(--background-light);
  border-radius: 1.5rem;
  margin-bottom: 2rem;
}

.avatar {
  width: 100px;
  height: 100px;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  font-weight: 700;
}

.user-info h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 0.25rem;
}

.user-info p {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.member-since {
  margin-top: 0.5rem;
  font-size: 0.75rem !important;
  color: var(--text-muted) !important;
}

.profile-content .section {
  background: var(--background-light);
  border-radius: 1.5rem;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.section h2 {
  font-size: 1.125rem;
  font-weight: 600;
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-color);
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.empty-state p {
  margin-bottom: 1rem;
}

.watch-history-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.history-item {
  background: var(--background-dark);
  border-radius: 0.75rem;
  overflow: hidden;
}

.history-anime {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  text-decoration: none;
  color: inherit;
}

.history-anime img {
  width: 60px;
  height: 80px;
  border-radius: 0.5rem;
  object-fit: cover;
}

.history-info h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.history-info p {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.settings-card {
  display: flex;
  gap: 1rem;
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
  }
}
</style>