<template>
  <div class="profile">
    <div class="container">
      <div class="profile-header">
        <div class="avatar-wrapper">
          <label class="avatar" for="avatar-upload" :style="{ backgroundImage: avatarUrl ? `url(${avatarUrl})` : null }">
            <span class="user-avatar" :style="{ backgroundImage: `url(${user?.avatarUrl || '/src/assets/avatars/default.svg'})` }"></span>
            <span class="upload-hint">点击更换头像</span>
          </label>
          <input id="avatar-upload" type="file" accept="image/*" class="avatar-upload-input" @change="handleAvatarChange" />
        </div>
        <div class="user-info">
          <h1>{{ user?.username }}</h1>
          <div class="signature-wrapper">
            <p v-if="!editingSignature" class="signature-text" @click="startEditSignature">
              {{ userSignature || '点击添加个性签名...' }}
            </p>
            <input
              v-else
              ref="signatureInputRef"
              v-model="userSignature"
              type="text"
              class="signature-input"
              maxlength="100"
              placeholder="写下你的个性签名..."
              @blur="saveSignature"
              @keyup.enter="saveSignature"
            />
          </div>
          <p class="member-since" v-if="user?.createdAt">
            加入于 {{ formatDate(user.createdAt) }}
          </p>
        </div>
      </div>

      <div class="profile-content">
        <section class="section">
          <h2>我的收藏</h2>
          <div v-if="favorites.length > 0" class="favorites-grid">
            <AnimeCard v-for="anime in favorites" :key="anime.id" :anime="anime" :card-width="160" :card-height="260" />
          </div>
          <div v-else class="empty-state">
            <p>还没有收藏任何番剧</p>
            <router-link to="/browse" class="btn btn-primary">去浏览</router-link>
          </div>
        </section>

        <section class="section">
          <h2>观看历史</h2>
          <div v-if="watchHistory.length > 0" class="watch-history-list">
            <div class="history-item" v-for="item in watchHistory" :key="item.anime.id">
              <router-link :to="`/anime/${item.anime.id}`" class="history-anime">
                <img :src="item.anime.imageUrl || 'https://via.placeholder.com/80x120/1e293b/475569?text=?'" :alt="item.anime.title" />
                <div class="history-info">
                  <h4>{{ item.anime.title }}</h4>
                  <p>观看进度: {{ item.progress }}%</p>
                  <span v-if="item.completed" class="badge badge-success">已完成</span>
                </div>
              </router-link>
            </div>
          </div>
          <div v-else class="empty-state">
            <p>还没有观看记录</p>
          </div>
        </section>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AnimeCard from '../components/AnimeCard.vue'

const router = useRouter()
const authStore = useAuthStore()

const user = computed(() => authStore.user)
const favorites = ref([])
const watchHistory = ref([])
const userSignature = ref('')
const editingSignature = ref(false)
const signatureInputRef = ref(null)
const avatarUrl = ref('')
const defaultAvatarUrl = '/src/assets/avatars/default.svg'

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN')
}

const startEditSignature = async () => {
  userSignature.value = user.value?.signature || ''
  editingSignature.value = true
  await nextTick()
  signatureInputRef.value?.focus()
}

const saveSignature = async () => {
  editingSignature.value = false
  const newSig = userSignature.value?.trim() || ''
  userSignature.value = newSig
  try {
    await fetch('/api/user/signature', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify({ signature: newSig })
    })
    if (authStore.user) {
      authStore.user.signature = newSig
    }
  } catch (error) {
    console.error('Failed to save signature:', error)
  }
}

const handleAvatarChange = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  const reader = new FileReader()
  reader.onload = async (e) => {
    avatarUrl.value = e.target.result
    
    const formData = new FormData()
    formData.append('avatar', file)
    
    try {
      const response = await fetch('/api/user/avatar', {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${authStore.token}`
        },
        body: formData
      })
      const data = await response.json()
      if (data.success && data.data && authStore.user) {
        authStore.user.avatarUrl = data.data
      }
    } catch (error) {
      console.error('Failed to upload avatar:', error)
    }
  }
  reader.readAsDataURL(file)
}

const loadAvatar = () => {
  if (user.value?.avatarUrl) {
    avatarUrl.value = user.value.avatarUrl
  } else {
    avatarUrl.value = defaultAvatarUrl
  }
}

watch(() => user.value?.signature, (val) => {
  if (!editingSignature.value) {
    userSignature.value = val || ''
  }
}, { immediate: true })

watch(() => user.value?.avatar, () => {
  loadAvatar()
}, { immediate: true })

const fetchFavorites = async () => {
  try {
    const response = await fetch('/api/user/favorites', {
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })
    const data = await response.json()
    favorites.value = data.data || []
  } catch (error) {
    console.error('Failed to fetch favorites:', error)
  }
}

const fetchWatchHistory = async () => {
  try {
    const response = await fetch('/api/user/watch-history', {
      headers: {
        'Authorization': `Bearer ${authStore.token}`
      }
    })
    const data = await response.json()
    watchHistory.value = data.data || []
  } catch (error) {
    console.error('Failed to fetch watch history:', error)
  }
}

onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.user) {
    await authStore.verifyToken()
  }
  if (authStore.isAuthenticated) {
    fetchFavorites()
    fetchWatchHistory()
  }
})
</script>

<style scoped>
.profile {
  padding: 2rem 0;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 2rem;
  padding: 2rem;
  background: var(--background-median);
  border-radius: 1.5rem;
  margin-bottom: 2rem;
}

.avatar-wrapper {
  position: relative;
}

.avatar {
  width: 100px;
  height: 100px;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  font-weight: 700;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.avatar:hover .upload-hint {
  opacity: 1;
}

.upload-hint {
  position: absolute;
  bottom: 8px;
  font-size: 0.625rem;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 8px;
  border-radius: 10px;
  opacity: 0;
  transition: opacity 0.2s;
  white-space: nowrap;
}

.avatar:hover::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.2);
}

.avatar-upload-input {
  display: none;
}

.user-info h1 {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 0.25rem;
}

.user-info p {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.signature-wrapper {
  margin-top: 0.25rem;
}

.signature-text {
  color: var(--text-muted);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: color 0.2s;
  padding: 0.25rem 0;
}

.signature-text:hover {
  color: var(--primary-color);
}

.signature-input {
  width: 200%;
  max-width: 600px;
  padding: 0.375rem 0.75rem;
  background: var(--background-dark);
  border: 1px solid var(--primary-color);
  border-radius: 0.5rem;
  color: var(--text-primary);
  font-size: 0.8125rem;
  outline: none;
}

.member-since {
  margin-top: 0.5rem;
  font-size: 0.75rem !important;
  color: var(--text-muted) !important;
}

.profile-content .section {
  background: var(--background-median);
  border-radius: 1.5rem;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.section h2 {
  font-size: 1.125rem;
  font-weight: 600;
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-color);
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.empty-state p {
  margin-bottom: 1rem;
}

.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 160px);
  gap: 1.5rem;
  justify-content: left;
}

.watch-history-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.history-item {
  background: var(--background-dark);
  border-radius: 0.75rem;
  overflow: hidden;
}

.history-anime {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  text-decoration: none;
  color: inherit;
}

.history-anime img {
  width: 60px;
  height: 80px;
  border-radius: 0.5rem;
  object-fit: cover;
}

.history-info h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.history-info p {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
  }
}
</style>