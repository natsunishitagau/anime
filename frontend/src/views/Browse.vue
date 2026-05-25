<template>
  <div class="browse">
    <div class="container">
      <h1>浏览番剧</h1>

      <div class="filters">
        <div style="display: flex; flex-wrap: wrap; gap: 1rem;">
          <div class="filter-group">
            <label>类型</label>
            <select v-model="filters.type" @change="fetchAnime" class="input">
              <option value="">全部</option>
              <option v-for="t in filterOptions.types" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>

          <div class="filter-group">
            <label>来源</label>
            <select v-model="filters.source" @change="fetchAnime" class="input">
              <option value="">全部</option>
              <option v-for="s in filterOptions.sources" :key="s" :value="s">{{ s }}</option>
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
            <label>题材</label>
            <select v-model="filters.genre" @change="fetchAnime" class="input">
              <option value="">全部</option>
              <option v-for="g in filteredGenres" :key="g" :value="g">{{ g }}</option>
            </select>
          </div>

          <div class="filter-group">
            <label>状态</label>
            <select v-model="filters.status" @change="fetchAnime" class="input">
              <option value="">全部</option>
              <option v-for="s in filterOptions.statuses" :key="s" :value="s">{{ s }}</option>
            </select>
          </div>
        </div>
        <button @click="clearFilters" class="btn btn-secondary">清除筛选</button>
      </div>

      <div class="results-info">
        <p>共找到 {{ totalElements > 100 ? 100 : totalElements }} 部番剧</p>
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

      <div v-if="!loading && animeList.length > 0 && totalPages > 1" class="pagination">
        <button @click="prevPage" :disabled="currentPage === 1" class="btn btn-secondary">上一页</button>
        <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button @click="nextPage" :disabled="currentPage >= totalPages" class="btn btn-secondary">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAnimeStore } from '../stores/anime'
import AnimeCard from '../components/AnimeCard.vue'

const route = useRoute()
const router = useRouter()
const animeStore = useAnimeStore()

const allAnime = ref([])
const animeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const totalElements = ref(0)
const totalPages = ref(1)

const PAGE_SIZE = 20
const MAX_RESULTS = 100

const filters = reactive({
  type: '',
  source: '',
  season: '',
  year: '',
  genre: '',
  status: ''
})

const filterOptions = reactive({
  types: [],
  sources: [],
  seasons: [],
  years: [],
  genres: [],
  statuses: []
})

const filteredGenres = computed(() => {
  return filterOptions.genres.filter(genre => genre && typeof genre === 'string' && genre.trim() !== '')
})

const paginateAnime = () => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  const end = start + PAGE_SIZE
  animeList.value = allAnime.value.slice(start, end)
}

const fetchAnime = async () => {
  loading.value = true
  currentPage.value = 1
  allAnime.value = []
  try {
    if (route.query.q) {
      const pageCount = Math.ceil(MAX_RESULTS / PAGE_SIZE)
      const allResults = []
      
      for (let page = 1; page <= pageCount; page++) {
        const result = await animeStore.searchAnimePage(route.query.q, page, PAGE_SIZE)
        if (result.content && result.content.length > 0) {
          allResults.push(...result.content)
          if (allResults.length >= MAX_RESULTS) break
        } else {
          break
        }
      }
      
      allAnime.value = allResults.slice(0, MAX_RESULTS)
    } else {
      const params = {
        limit: MAX_RESULTS,
        offset: 0
      }
      if (filters.type) params.type = filters.type
      if (filters.source) params.source = filters.source
      if (filters.season) params.season = filters.season
      if (filters.year) params.year = parseInt(filters.year)
      if (filters.genre) params.genre = filters.genre
      if (filters.status) params.status = filters.status

      await animeStore.fetchAnimeList(params)
      allAnime.value = animeStore.animeList.slice(0, MAX_RESULTS)
    }
    
    totalElements.value = allAnime.value.length
    totalPages.value = Math.ceil(totalElements.value / PAGE_SIZE)
    paginateAnime()
  } finally {
    loading.value = false
  }
}

const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
    paginateAnime()
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
    paginateAnime()
  }
}

const clearFilters = async () => {
  filters.type = ''
  filters.source = ''
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
  if (newQuery.source) filters.source = newQuery.source
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
    if (route.query.source) filters.source = route.query.source
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
  justify-content: space-between;
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
  min-width: 120px;
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

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 2rem;
}

.pagination .btn {
  padding: 0.5rem 1.5rem;
}

.pagination .btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  color: var(--text-secondary);
  font-size: 0.875rem;
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
