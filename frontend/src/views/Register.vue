<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-card">
        <h1>注册</h1>
        <p class="auth-subtitle">加入 AnimeHub 发现更多精彩</p>

        <form @submit.prevent="handleRegister" class="auth-form">
          <div class="form-group">
            <label>用户名</label>
            <input 
              type="text" 
              v-model="username" 
              class="input" 
              placeholder="3-30个字符"
              minlength="3"
              maxlength="30"
              required
            />
          </div>

          <div class="form-group">
            <label>邮箱</label>
            <input 
              type="email" 
              v-model="email" 
              class="input" 
              placeholder="请输入邮箱"
              required
            />
          </div>

          <div class="form-group">
            <label>密码</label>
            <input 
              type="password" 
              v-model="password" 
              class="input" 
              placeholder="至少6个字符"
              minlength="6"
              required
            />
          </div>

          <div class="form-group">
            <label>确认密码</label>
            <input 
              type="password" 
              v-model="confirmPassword" 
              class="input" 
              placeholder="再次输入密码"
              required
            />
          </div>

          <div v-if="error" class="error-message">
            {{ error }}
          </div>

          <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>

        <p class="auth-footer">
          已有账号? <router-link to="/login">立即登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')

const loading = computed(() => authStore.loading)

const handleRegister = async () => {
  error.value = ''

  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  if (password.value.length < 6) {
    error.value = '密码至少需要6个字符'
    return
  }

  const success = await authStore.register(username.value, email.value, password.value)
  if (success) {
    router.push('/')
  } else {
    error.value = authStore.error || '注册失败'
  }
}
</script>

<style scoped>
.auth-page {
  min-height: calc(100vh - 8rem);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1.5rem;
}

.auth-container {
  width: 100%;
  max-width: 400px;
}

.auth-card {
  background: var(--background-light);
  border-radius: 1.5rem;
  padding: 2.5rem;
}

.auth-card h1 {
  font-size: 1.75rem;
  font-weight: 700;
  text-align: center;
  margin-bottom: 0.5rem;
}

.auth-subtitle {
  text-align: center;
  color: var(--text-secondary);
  margin-bottom: 2rem;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-group label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-secondary);
}

.error-message {
  padding: 0.75rem;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid var(--error-color);
  border-radius: 0.5rem;
  color: var(--error-color);
  font-size: 0.875rem;
  text-align: center;
}

.btn-full {
  width: 100%;
  padding: 0.875rem;
  font-size: 1rem;
}

.auth-footer {
  text-align: center;
  margin-top: 1.5rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.auth-footer a {
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 500;
}

.auth-footer a:hover {
  text-decoration: underline;
}
</style>