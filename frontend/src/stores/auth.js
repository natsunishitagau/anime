import { defineStore } from 'pinia'
import axios from '../utils/axios'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    token: localStorage.getItem('token') || null,
    loading: false,
    error: null
  }),

  getters: {
    isAuthenticated: (state) => !!state.token,
    currentUser: (state) => state.user
  },

  actions: {
    async register(username, email, password) {
      this.loading = true
      this.error = null
      try {
        const response = await axios.post(`/auth/register`, {
          username,
          email,
          password
        })
        if (response.data.success) {
          this.token = response.data.data.token
          this.user = response.data.data.user
          localStorage.setItem('token', this.token)
          axios.defaults.headers.common['Authorization'] = `Bearer ${this.token}`
          return true
        }
      } catch (error) {
        this.error = error.response?.data?.message || 'Registration failed'
        return false
      } finally {
        this.loading = false
      }
    },

    async login(username, password) {
      this.loading = true
      this.error = null
      try {
        const response = await axios.post(`/auth/login`, {
          username,
          password
        })
        if (response.data.success) {
          this.token = response.data.data.token
          this.user = response.data.data.user
          localStorage.setItem('token', this.token)
          axios.defaults.headers.common['Authorization'] = `Bearer ${this.token}`
          return true
        }
      } catch (error) {
        this.error = error.response?.data?.message || 'Login failed'
        return false
      } finally {
        this.loading = false
      }
    },

    async verifyToken() {
      if (!this.token) return false
      
      try {
        axios.defaults.headers.common['Authorization'] = `Bearer ${this.token}`
        const response = await axios.get(`/auth/verify`)
        if (response.data.success) {
          this.user = response.data.data
          return true
        }
      } catch (error) {
        this.logout()
      }
      return false
    },

    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('token')
      delete axios.defaults.headers.common['Authorization']
    },

    clearError() {
      this.error = null
    }
  }
})