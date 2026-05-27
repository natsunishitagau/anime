<template>
  <nav class="navbar">
    <div class="navbar-container">
      <div class="navbar-brand">
        <router-link to="/" class="logo">
          <span class="logo-icon">🎬</span>
          <span class="logo-text">AnimeHub</span>
        </router-link>
      </div>

      <div class="navbar-actions">
        <router-link to="/" class="nav-link">
          <span class="nav-icon">🏠</span>
          <span class="nav-text">首页</span>
        </router-link>
        <router-link to="/browse" class="nav-link">
          <span class="nav-icon">📺</span>
          <span class="nav-text">浏览</span>
        </router-link>
        <router-link to="/game" class="nav-link">
          <span class="nav-icon">🎮</span>
          <span class="nav-text">游戏</span>
        </router-link>
        
        <div class="search-box">
          <input 
            type="text" 
            v-model="searchQuery" 
            @keyup.enter="handleSearch"
            placeholder="搜索番剧..."
            class="search-input"
          />
          <button @click="handleSearch" class="search-btn">🔍</button>
        </div>
      </div>

      <div class="navbar-user">
        <template v-if="isAuthenticated">
          <router-link to="/favorites" class="nav-link">
            <span class="nav-icon">⭐</span>
            <span class="nav-text">收藏</span>
          </router-link>
          <router-link to="/messages" class="nav-link message-link">
            <span class="nav-icon">📩</span>
            <span class="nav-text">消息</span>
            <span v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>
          
          <router-link to="/profile" class="nav-link user-link">
            <span class="user-avatar" :style="{ backgroundImage: `url(${user?.avatarUrl || '/src/assets/avatars/default.svg'})` }"></span>
            <span class="user-name">{{ user?.username }}</span>
          </router-link>
          <button @click="handleLogout" class="btn btn-outline logout-btn">退出</button>
        </template>
        
        <template v-else>
          <router-link to="/login" class="nav-link">
            <span class="nav-icon">🔑</span>
            <span class="nav-text">登录</span>
          </router-link>
          <router-link to="/register" class="btn btn-primary">注册</router-link>
        </template>
      </div>

      <button class="mobile-menu-btn" @click="isMobileMenuOpen = !isMobileMenuOpen">
        {{ isMobileMenuOpen ? '✕' : '☰' }}
      </button>
    </div>

    <div v-if="isMobileMenuOpen" class="mobile-menu">
      <router-link to="/" class="mobile-link" @click="isMobileMenuOpen = false">首页</router-link>
      <router-link to="/browse" class="mobile-link" @click="isMobileMenuOpen = false">浏览</router-link>
      <router-link to="/game" class="mobile-link" @click="isMobileMenuOpen = false">游戏</router-link>
      
      <template v-if="isAuthenticated">
        <router-link to="/favorites" class="mobile-link" @click="isMobileMenuOpen = false">收藏</router-link>
        <router-link to="/profile" class="mobile-link" @click="isMobileMenuOpen = false">个人资料</router-link>
        <button @click="handleLogout" class="mobile-link logout">退出</button>
      </template>
      
      <template v-else>
        <router-link to="/login" class="mobile-link" @click="isMobileMenuOpen = false">登录</router-link>
        <router-link to="/register" class="mobile-link" @click="isMobileMenuOpen = false">注册</router-link>
      </template>
    </div>
  </nav>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import axios from '../utils/axios'
import { eventBus, MESSAGE_EVENTS } from '../utils/eventBus'

const router = useRouter()
const authStore = useAuthStore()

const searchQuery = ref('')
const isMobileMenuOpen = ref(false)
const unreadCount = ref(0)

const isAuthenticated = computed(() => authStore.isAuthenticated)
const user = computed(() => authStore.currentUser)

const fetchUnreadCount = async () => {
  if (!isAuthenticated.value) {
    unreadCount.value = 0
    return
  }
  try {
    const response = await axios.get('/user/messages/unread/count')
    if (response.data.success) {
      unreadCount.value = response.data.data.count
    }
  } catch (error) {
    console.error('Failed to fetch unread count:', error)
  }
}

const handleUnreadCountChanged = () => {
  fetchUnreadCount()
}

onMounted(() => {
  fetchUnreadCount()
  eventBus.on(MESSAGE_EVENTS.UNREAD_COUNT_CHANGED, handleUnreadCountChanged)
})

onUnmounted(() => {
  eventBus.off(MESSAGE_EVENTS.UNREAD_COUNT_CHANGED, handleUnreadCountChanged)
})

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ name: 'Search', query: { keyword: searchQuery.value } })
    searchQuery.value = ''
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/')
  isMobileMenuOpen.value = false
}
</script>

<style scoped>
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  background: rgba(15, 23, 42, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-color);
  z-index: 1000;
}

.navbar-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
  height: 5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
}

.navbar-brand {
  display: flex;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  text-decoration: none;
  color: var(--text-primary);
}

.logo-icon {
  font-size: 1.75rem;
}

.logo-text {
  font-size: 1.5rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.navbar-actions {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.navbar-user {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.search-box {
  display: flex;
  gap: 0.5rem;
}

.search-input {
  padding: 0.5rem 1rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 9999px;
  color: var(--text-primary);
  font-size: 0.875rem;
  width: 180px;
}

.search-input:focus {
  outline: none;
  border-color: var(--primary-color);
}

.search-btn {
  padding: 0;
  background: none;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  font-size: 1.25rem;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.search-btn:hover {
  color: var(--text-primary);
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: var(--text-secondary);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.nav-link:hover {
  color: var(--text-primary);
}

.nav-icon {
  font-size: 1.125rem;
}

.nav-text {
  font-size: 1rem;
}

.message-link {
  position: relative;
}

.badge {
  position: absolute;
  top: -0.375rem;
  right: -0.5rem;
  background: var(--error-color);
  color: white;
  font-size: 0.625rem;
  padding: 0.125rem 0.375rem;
  border-radius: 9999px;
  min-width: 1rem;
  text-align: center;
}

.user-link {
  gap: 0.5rem;
}

.user-avatar {
  width: 2rem;
  height: 2rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.user-name {
  color: var(--text-primary);
  font-size: 1rem;
}

.logout-btn {
  padding: 0.5rem 1rem;
  font-size: 1rem;
}

.mobile-menu-btn {
  display: none;
  background: none;
  border: none;
  font-size: 1.5rem;
  color: var(--text-primary);
  cursor: pointer;
}

.mobile-menu {
  display: none;
  padding: 1rem 1.5rem;
  background: var(--background-light);
  border-bottom: 1px solid var(--border-color);
}

.mobile-link {
  display: block;
  padding: 0.75rem 0;
  color: var(--text-secondary);
  text-decoration: none;
  border-bottom: 1px solid var(--border-color);
}

.mobile-link:last-child {
  border-bottom: none;
}

.mobile-link.logout {
  background: none;
  border: none;
  color: var(--error-color);
  cursor: pointer;
  width: 100%;
  text-align: left;
  font-size: 1rem;
}

@media (max-width: 992px) {
  .navbar-actions, .navbar-user {
    display: none;
  }

  .mobile-menu-btn {
    display: block;
  }

  .mobile-menu {
    display: block;
  }
}
</style>
