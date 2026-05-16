<template>
  <div class="browse">
    <div class="container">
      <h1>浏览番剧</h1>

      <div class="filters">
        <div class="filter-group">
          <label>类型</label>
          <select v-model="filters.type" @change="fetchAnime" class="input">
            <option value="">全部</option>
            <option v-for="type in filterOptions.types" :key="type" :value="type">{{ type }}</option>
          </select>
        </div>

        <div class="filter-group">
          <label>季度</label>
          <select v-model="filters.season" @change="fetchAnime" class="input">
            <option value="">全部</option>
            <option v-for="season in filterOptions.seasons" :key="season" :value="season">{{ season }}</option>
          </select>
        </div>

        <div class="filter-group">
          <label>年份</label>
          <select v-model="filters.year" @change="fetchAnime" class="input">
            <option value="">全部</option>
            <option v-for="year in filterOptions.years" :key="year" :value="year">{{ year }}</option>
          </select>
        </div>

        <div class="filter-group">
          <label>类型分类</label>
          <select v-model="filters.genre" @change="fetchAnime" class="input">
            <option value="">全部</option>
            <option v-for="genre in filterOptions.genres" :key="genre" :value="genre">{{ genre }}</option>
          </select>
        </div>

        <div class="filter-group">
          <label>状态</label>
          <select v-model="filters.status" @change="fetchAnime" class="input">
            <option value="">全部</option>
            <option v-for="status in filterOptions.statuses" :key="status" :value="status">{{ status }}</option>
          </select>
        </div>

        <button @click="clearFilters" class="btn btn-secondary">清除筛选</button>
      </div>

      <div class="results-info">
        <p>共找到 {{ animeList.length }} 部番剧</p>
      </div>

      <div v-if="loading" class="loading">
        <div class="spinner"></div>
      </div>

      <div v-else-if="animeList.length === 0" class="empty-state">
        <p>没有找到符合条件的番剧</p>
        <button @click="clearFilters" class="btn btn-primary">清除筛选</button>
      </div>

      <div v-else class="grid grid-5">
        <AnimeCard v-for="anime in animeList" :key="anime.id" :anime="anime" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAnimeStore } from '../stores/anime'
import AnimeCard from '../components/AnimeCard.vue'

const route = useRoute()
const router = useRouter()
const animeStore = useAnimeStore()

const animeList = ref([])
const loading = ref(false)

const filters = reactive({
  type: '',
  season: '',
  year: '',
  genre: '',
  status: ''
})

const filterOptions = reactive({
  types: [],
  seasons: [],
  years: [],
  genres: [],
  statuses: []
})

const fetchAnime = async () => {
  loading.value = true
  try {
    if (route.query.q) {
      animeList.value = await animeStore.searchAnime(route.query.q)
    } else {
      const params = {}
      if (filters.type) params.type = filters.type
      if (filters.season) params.season = filters.season
      if (filters.year) params.year = filters.year
      if (filters.genre) params.genre = filters.genre
      if (filters.status) params.status = filters.status
      
      await animeStore.fetchAnimeList(params)
      animeList.value = animeStore.animeList
    }
  } finally {
    loading.value = false
  }
}

const clearFilters = async () => {
  filters.type = ''
  filters.season = ''
  filters.year = ''
  filters.genre = ''
  filters.status = ''
  if (route.query.q) {
    await router.push({ name: 'Browse' })
  }
  fetchAnime()
}

const fetchFilters = async () => {
  await animeStore.fetchFilters()
  Object.assign(filterOptions, animeStore.filters)
}

watch(() => route.query, (newQuery) => {
  if (newQuery.type) filters.type = newQuery.type
  if (newQuery.season) filters.season = newQuery.season
  if (newQuery.year) filters.year = newQuery.year
  if (newQuery.genre) filters.genre = newQuery.genre
  if (newQuery.status) filters.status = newQuery.status
  if (newQuery.q) {
    filters.q = newQuery.q
  }
  fetchAnime()
}, { deep: true })

onMounted(() => {
  fetchFilters()
  
  if (route.query) {
    if (route.query.type) filters.type = route.query.type
    if (route.query.season) filters.season = route.query.season
    if (route.query.year) filters.year = route.query.year
    if (route.query.genre) filters.genre = route.query.genre
    if (route.query.status) filters.status = route.query.status
    if (route.query.q) filters.q = route.query.q
  }
  
  fetchAnime()
})
</script>

<style scoped>
.browse {
  padding: 2rem 0;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

h1 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 2rem;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--background-light);
  border-radius: 1rem;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 150px;
}

.filter-group label {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-secondary);
  text-transform: uppercase;
}

.filter-group .input {
  padding: 0.5rem 1rem;
}

.results-info {
  margin-bottom: 1.5rem;
  color: var(--text-secondary);
}

.empty-state {
  text-align: center;
  padding: 4rem 2rem;
}

.empty-state p {
  color: var(--text-secondary);
  margin-bottom: 1.5rem;
}

.loading {
  display: flex;
  justify-content: center;
  padding: 4rem;
}

@media (max-width: 768px) {
  .filters {
    flex-direction: column;
  }

  .filter-group {
    width: 100%;
  }
}
</style>