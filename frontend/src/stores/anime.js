import { defineStore } from 'pinia'
import axios from '../utils/axios'

const API_URL = '/anime'

export const useAnimeStore = defineStore('anime', {
  state: () => ({
    animeList: [],
    currentAnime: null,
    recommendations: [],
    trending: [],
    topRated: [],
    filters: {
      genres: [],
      seasons: [],
      years: [],
      types: [],
      statuses: []
    },
    loading: false,
    error: null
  }),

  actions: {
    async fetchAnimeList(params = {}) {
      this.loading = true
      try {
        const queryParams = new URLSearchParams()
        Object.entries(params).forEach(([key, value]) => {
          if (value) queryParams.append(key, value)
        })
        
        const response = await axios.get(`${API_URL}?${queryParams}`)
        this.animeList = response.data.data
      } catch (error) {
        this.error = error.response?.data?.message || 'Failed to fetch anime'
      } finally {
        this.loading = false
      }
    },

    async fetchAnimeById(id) {
      this.loading = true
      try {
        const response = await axios.get(`${API_URL}/${id}`)
        this.currentAnime = response.data.data
        return response.data.data
      } catch (error) {
        this.error = error.response?.data?.message || 'Failed to fetch anime details'
        return null
      } finally {
        this.loading = false
      }
    },

    async fetchTrending(limit = 10) {
      try {
        const response = await axios.get(`${API_URL}/trending?limit=${limit}`)
        this.trending = response.data.data
      } catch (error) {
        console.error('Failed to fetch trending:', error)
      }
    },

    async fetchTopRated(limit = 20) {
      try {
        const response = await axios.get(`${API_URL}/top-rated?limit=${limit}`)
        this.topRated = response.data.data
      } catch (error) {
        console.error('Failed to fetch top rated:', error)
      }
    },

    async fetchRecommendations(limit = 20) {
      try {
        const response = await axios.get(`${API_URL}/recommendations?limit=${limit}`)
        return response.data.data
      } catch (error) {
        console.error('Failed to fetch recommendations:', error)
        return []
      }
    },

    async fetchFilters() {
      try {
        const response = await axios.get(`${API_URL}/filters`)
        this.filters = response.data.data
      } catch (error) {
        console.error('Failed to fetch filters:', error)
      }
    },

    async searchAnime(query) {
      this.loading = true
      try {
        const response = await axios.get(`${API_URL}/search?q=${query}`)
        return response.data.data
      } catch (error) {
        this.error = error.response?.data?.message || 'Search failed'
        return []
      } finally {
        this.loading = false
      }
    },

    async searchAnimePage(keyword, page = 1, size = 20) {
      this.loading = true
      try {
        const response = await axios.get(`${API_URL}/search/page`, {
          params: {
            keyword,
            page,
            size
          }
        })
        if (!response.data.success) {
          throw new Error(response.data.message || 'Search failed')
        }
        return response.data.data
      } catch (error) {
        const errorMessage = error.response?.data?.message || error.message || 'Search failed'
        this.error = errorMessage
        throw error
      } finally {
        this.loading = false
      }
    },

    async toggleFavorite(animeId) {
      try {
        const response = await axios.post(`${API_URL}/${animeId}/favorite`)
        return response.data.success
      } catch (error) {
        return false
      }
    },

    async rateAnime(animeId, rating) {
      try {
        const response = await axios.post(`${API_URL}/${animeId}/rate?rating=${rating}`)
        return response.data.success
      } catch (error) {
        return false
      }
    },

    async addReview(animeId, comment, parentId = null) {
      try {
        let url = `${API_URL}/${animeId}/review?comment=${encodeURIComponent(comment || '')}`
        if (parentId) {
          url += `&parentId=${parentId}`
        }
        const response = await axios.post(url)
        return response.data.success
      } catch (error) {
        return false
      }
    },

    async likeReview(animeId, reviewId) {
      try {
        const response = await axios.post(`${API_URL}/${animeId}/review/${reviewId}/like`)
        return response.data.data
      } catch (error) {
        return null
      }
    },

    async deleteReview(animeId, reviewId) {
      try {
        const response = await axios.delete(`${API_URL}/${animeId}/review/${reviewId}`)
        return response.data.success
      } catch (error) {
        return false
      }
    },

    async fetchReviewsTree(animeId) {
      try {
        const response = await axios.get(`${API_URL}/${animeId}/reviews/tree`)
        return response.data.data
      } catch (error) {
        console.error('Failed to fetch reviews tree:', error)
        return []
      }
    }
  }
})
