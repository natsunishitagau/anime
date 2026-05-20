<template>
  <div class="search-result">
    <div class="container">
      <div class="search-header">
        <h1>搜索结果</h1>
        <p class="search-keyword">已显示搜索名称"{{ searchKeyword }}"的结果</p>
      </div>

      <div v-if="loading" class="loading">
        <div class="spinner"></div>
      </div>

      <div v-else-if="totalElements === 0" class="empty-state">
        <p>没有找到相关的番剧</p>
      </div>

      <div v-else>
        <div class="results-info">
          <p>共找到 {{ totalElements }} 部番剧</p>
        </div>

        <div class="grid grid-5">
          <AnimeCard v-for="anime in animeList" :key="anime.id" :anime="anime" />
        </div>

        <div v-if="totalPages > 1" class="pagination">
          <button 
            @click="prevPage" 
            :disabled="currentPage <= 1" 
            class="btn btn-outline pagination-btn"
          >
            上一页
          </button>
          
          <span class="page-info">
            第 {{ currentPage }} / {{ totalPages }} 页
          </span>
          
          <button 
            @click="nextPage" 
            :disabled="currentPage >= totalPages" 
            class="btn btn-outline pagination-btn"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAnimeStore } from '../stores/anime';
import AnimeCard from '../components/AnimeCard.vue';
const route = useRoute();
const router = useRouter();
const animeStore = useAnimeStore();
const animeList = ref([]);
const loading = ref(false);
const currentPage = ref(1);
const totalPages = ref(1);
const totalElements = ref(0);
const pageSize = 20;
const searchKeyword = computed(() => route.query.keyword || '');
const fetchSearchResults = async () => {
 if (!searchKeyword.value)
 return;
 loading.value = true;
 try {
 const result = await animeStore.searchAnimePage(searchKeyword.value, currentPage.value, pageSize);
 animeList.value = result.content || [];
 totalPages.value = result.totalPages || 1;
 totalElements.value = result.totalElements || 0;
 }
 finally {
 loading.value = false;
 }
};
const prevPage = () => {
 if (currentPage.value > 1) {
 currentPage.value--;
 fetchSearchResults();
 }
};
const nextPage = () => {
 if (currentPage.value < totalPages.value) {
 currentPage.value++;
 fetchSearchResults();
 }
};
watch(() => route.query.keyword, (newKeyword) => {
 if (newKeyword) {
 currentPage.value = 1;
 fetchSearchResults();
 }
});
onMounted(() => {
 if (searchKeyword.value) {
 fetchSearchResults();
 }
});
</script>

<style scoped>
.search-result {
  padding: 2rem 0;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.search-header {
  margin-bottom: 2rem;
}

.search-header h1 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.search-keyword {
  color: var(--text-secondary);
  font-size: 1.1rem;
}

.search-keyword strong {
  color: var(--primary-color);
  font-weight: 600;
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

.pagination-btn {
  padding: 0.5rem 1.5rem;
}

.pagination-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .search-header h1 {
    font-size: 1.5rem;
  }
}
</style>
