<template>
  <div class="home">
    <section class="hero">
      <div class="hero-content">
        <h1>发现你的下一部最爱番剧</h1>
        <p>基于你的喜好，智能推荐个性化内容</p>
        <router-link to="/browse" class="btn btn-primary btn-large">开始探索</router-link>
      </div>
    </section>

    <section class="section" v-if="authStore.isAuthenticated">
      <div class="container">
        <div class="section-header">
          <h2>🎯 为你推荐</h2>
          <router-link to="/browse" class="see-more">查看更多 →</router-link>
        </div>
        <div v-if="recommendationsLoading" class="loading-container">
          <div class="loading-spinner"></div>
          <p>正在获取推荐内容...</p>
        </div>
        <div v-else-if="recommendations.length > 0" class="grid grid-5">
          <AnimeCard v-for="anime in recommendations" :key="anime.id" :anime="anime" />
        </div>
        <div v-else class="empty-state">
          <p>暂无推荐内容，快去收藏或观看一些番剧吧</p>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-header">
          <div class="anime-select">
            <img src="../assets/anime-icon.svg" alt="番剧" class="section-icon" /> 
            <h2>番剧推荐</h2>
            <div class="main-tabs">
              <button 
                v-for="tab in mainTabs" 
                :key="tab.value"
                :class="['main-tab', { active: currentMainTab === tab.value }]"
                @click="currentMainTab = tab.value"
              >
                {{ tab.icon }} {{ tab.label }}
              </button>
          </div>
          </div>
          <div class="text-center mt-4">
            <router-link :to="currentTabLink" class="see-more inline-block">查看更多 →</router-link>
          </div>
        </div>
        
        <div v-if="currentMainTab === 'seasonal'" class="season-tabs-wrapper">
          <div class="season-tabs">
            <button 
              v-for="season in seasons" 
              :key="season.value"
              :class="['season-tab', { active: currentSeason === season.value }]"
              @click="currentSeason = season.value; fetchSeasonalAnime()"
            >
              {{ season.label }}
            </button>
          </div>
        </div>
        
        <div class="grid grid-5">
          <AnimeCard v-for="anime in currentTabAnime" :key="anime.id" :anime="anime" />
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2>🎭 分类浏览</h2>
        </div>
        <div class="genre-grid">
          <router-link 
            v-for="genre in genres" 
            :key="genre"
            :to="`/browse?genre=${genre}`"
            class="genre-card"
          >
            {{ genre }}
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAnimeStore } from '../stores/anime'
import { useAuthStore } from '../stores/auth'
import AnimeCard from '../components/AnimeCard.vue'

const animeStore = useAnimeStore()
const authStore = useAuthStore()

const recommendations = ref([])
const recommendationsLoading = ref(false)
const trending = ref([])
const topRated = ref([])
const seasonalAnime = ref([])
const currentSeason = ref('春季')
const currentMainTab = ref('trending')

const mainTabs = [
  { label: '热门番剧', value: 'trending', icon: '🔥', link: '/browse?sort=trending' },
  { label: '评分最高', value: 'top-rated', icon: '⭐', link: '/browse?sort=top-rated' },
  { label: '季度精选', value: 'seasonal', icon: '🎬', link: '/browse' }
]

const seasons = [
  { label: '春季', value: '春季' },
  { label: '夏季', value: '夏季' },
  { label: '秋季', value: '秋季' },
  { label: '冬季', value: '冬季' }
]

const genres = ['动作', '冒险', '喜剧', '剧情', '奇幻', '恋爱', '科幻', '运动', '超自然', '悬疑']

const currentTabAnime = computed(() => {
  switch (currentMainTab.value) {
    case 'trending':
      return trending.value
    case 'top-rated':
      return topRated.value
    case 'seasonal':
      return seasonalAnime.value
    default:
      return trending.value
  }
})

const currentTabLink = computed(() => {
  const tab = mainTabs.find(t => t.value === currentMainTab.value)
  return tab ? tab.link : '/browse'
})

const fetchSeasonalAnime = async () => {
  try {
    const response = await fetch(`/api/anime/seasonal?season=${currentSeason.value}&limit=10`)
    const data = await response.json()
    seasonalAnime.value = data.data || []
  } catch (error) {
    console.error('Failed to fetch seasonal anime:', error)
  }
}

onMounted(async () => {
  // 并行加载其他内容，不受推荐加载影响
  await Promise.all([
    animeStore.fetchTrending(10),
    animeStore.fetchTopRated(10),
    fetchSeasonalAnime()
  ])
  trending.value = animeStore.trending
  topRated.value = animeStore.topRated
  
  // 异步加载推荐，不阻塞其他内容
  if (authStore.isAuthenticated) {
    recommendationsLoading.value = true
    setTimeout(async () => {
      try {
        recommendations.value = await animeStore.fetchRecommendations(10)
      } catch (error) {
        console.error('Failed to fetch recommendations:', error)
        recommendations.value = []
      } finally {
        recommendationsLoading.value = false
      }
    }, 100)
  }
})
</script>

<style scoped>
.hero {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.2), rgba(236, 72, 153, 0.2)),
              url('https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1920') center/cover;
  padding: 6rem 1.5rem;
  text-align: center;
}

.hero-content {
  max-width: 600px;
  margin: 0 auto;
}

.hero h1 {
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  background: linear-gradient(135deg, var(--text-primary), var(--primary-color));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero p {
  font-size: 1.125rem;
  color: var(--text-secondary);
  margin-bottom: 2rem;
}

.btn-large {
  padding: 1rem 2rem;
  font-size: 1rem;
}

.section {
  padding: 3rem 0;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.anime-select {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.anime-select h2 {
  font-size: 1.5rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  margin-right: 1rem;
  gap: 1rem;
}

.section-icon {
  margin-top: 0.38rem;
  height: 1.5rem;
  object-fit: cover;
}

.see-more {
  color: var(--primary-color);
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
  transition: color 0.2s;
}

.see-more:hover {
  color: var(--secondary-color);
}

.main-tabs {
  display: flex;
  gap: 0.5rem;
}

.main-tab {
  padding: 0.5rem 1.5rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 9999px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.875rem;
}

.main-tab:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.main-tab.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.season-tabs-wrapper {
  margin-bottom: 1.5rem;
}

.season-tabs {
  display: flex;
  gap: 0.5rem;
}

.season-tab {
  padding: 0.5rem 1.5rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 9999px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.season-tab:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.season-tab.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.genre-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 1rem;
}

.genre-card {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  background: var(--background-light);
  border-radius: 1rem;
  text-decoration: none;
  color: var(--text-primary);
  font-weight: 500;
  transition: all 0.2s;
}

.genre-card:hover {
  background: var(--primary-color);
  transform: translateY(-2px);
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  gap: 1rem;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .hero h1 {
    font-size: 1.75rem;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
}
</style>