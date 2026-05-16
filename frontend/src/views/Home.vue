<template>
  <div class="home">
    <section class="hero">
      <div class="hero-content">
        <h1>发现你的下一部最爱番剧</h1>
        <p>基于你的喜好，智能推荐个性化内容</p>
        <router-link to="/browse" class="btn btn-primary btn-large">开始探索</router-link>
      </div>
    </section>

    <section class="section" v-if="recommendations.length > 0">
      <div class="container">
        <div class="section-header">
          <h2>🎯 为你推荐</h2>
          <router-link to="/browse" class="see-more">查看更多 →</router-link>
        </div>
        <div class="grid grid-5">
          <AnimeCard v-for="anime in recommendations" :key="anime.id" :anime="anime" />
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2>🔥 热门番剧</h2>
          <router-link to="/browse?sort=trending" class="see-more">查看更多 →</router-link>
        </div>
        <div class="grid grid-5">
          <AnimeCard v-for="anime in trending" :key="anime.id" :anime="anime" />
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2>⭐ 评分最高</h2>
          <router-link to="/browse?sort=top-rated" class="see-more">查看更多 →</router-link>
        </div>
        <div class="grid grid-5">
          <AnimeCard v-for="anime in topRated" :key="anime.id" :anime="anime" />
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2>🎬 季度精选</h2>
        </div>
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
        <div class="grid grid-5">
          <AnimeCard v-for="anime in seasonalAnime" :key="anime.id" :anime="anime" />
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
import { ref, onMounted } from 'vue'
import { useAnimeStore } from '../stores/anime'
import AnimeCard from '../components/AnimeCard.vue'

const animeStore = useAnimeStore()

const recommendations = ref([])
const trending = ref([])
const topRated = ref([])
const seasonalAnime = ref([])
const currentSeason = ref('春季')

const seasons = [
  { label: '春季', value: '春季' },
  { label: '夏季', value: '夏季' },
  { label: '秋季', value: '秋季' },
  { label: '冬季', value: '冬季' }
]

const genres = ['动作', '冒险', '喜剧', '剧情', '奇幻', '恋爱', '科幻', '运动', '超自然', '悬疑']

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
  recommendations.value = await animeStore.fetchRecommendations(10)
  await animeStore.fetchTrending(10)
  trending.value = animeStore.trending
  await animeStore.fetchTopRated(10)
  topRated.value = animeStore.topRated
  await fetchSeasonalAnime()
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

.section-header h2 {
  font-size: 1.5rem;
  font-weight: 600;
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

.season-tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
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