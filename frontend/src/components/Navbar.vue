<template>
  <nav class="navbar">
    <div class="navbar-container">
      <router-link to="/" class="logo">
        <span class="logo-icon">🎬</span>
        <span class="logo-text">AnimeHub</span>
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

      <div class="nav-links">
        <router-link to="/" class="nav-link">首页</router-link>
        <router-link to="/browse" class="nav-link">浏览</router-link>
        <router-link v-if="isAuthenticated" to="/favorites" class="nav-link">收藏</router-link>
      </div>

      <div class="auth-links">
        <template v-if="isAuthenticated">
          <router-link to="/profile" class="nav-link user-link">
            <span class="user-avatar" :style="{ backgroundImage: `url(${user?.avatarUrl || '/src/assets/avatars/default.svg'})` }"></span>
            <span class="user-name">{{ user?.username }}</span>
          </router-link>
          <button @click="handleLogout" class="btn btn-outline logout-btn">退出</button>
        </template>
        
        <template v-else>
          <router-link to="/login" class="nav-link">登录</router-link>
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
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const searchQuery = ref('')
const isMobileMenuOpen = ref(false)

const isAuthenticated = computed(() => authStore.isAuthenticated)
const user = computed(() => authStore.currentUser)

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ name: 'Browse', query: { q: searchQuery.value } })
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
  gap: 2rem;
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

.search-box {
  flex: 1;
  max-width: 400px;
  display: flex;
  gap: 0.5rem;
}

.search-input {
  flex: 1;
  padding: 0.625rem 1rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 9999px;
  color: var(--text-primary);
  font-size: 0.875rem;
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
  font-size: 1.5rem;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.search-btn:hover {
  color: var(--text-primary);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.auth-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  margin-left: auto;
}

.nav-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.nav-link:hover {
  color: var(--text-primary);
}

.user-link {
  display: flex;
  align-items: center;
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
}

.logout-btn {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
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

@media (max-width: 768px) {
  .search-box, .nav-links {
    display: none;
  }

  .mobile-menu-btn {
    display: block;
    margin-left: auto;
  }

  .mobile-menu {
    display: block;
  }
}
</style>