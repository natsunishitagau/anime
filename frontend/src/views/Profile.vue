<template>
  <div class="profile">
    <div class="container">
      <ConfirmDialog ref="confirmDialogRef" />
      <div class="profile-header">
        <div class="avatar-wrapper">
          <label class="avatar" for="avatar-upload" :style="{ backgroundImage: avatarUrl ? `url(${avatarUrl})` : null }">
            <span class="user-avatar" :style="{ backgroundImage: `url(${user?.avatarUrl || '/src/assets/avatars/default.svg'})` }"></span>
            <span class="upload-hint">点击更换头像</span>
          </label>
          <input id="avatar-upload" type="file" accept="image/*" class="avatar-upload-input" @change="handleAvatarChange" />
        </div>
        <div class="user-info">
          <h1 v-if="!editingUsername" class="username-text" @click="startEditUsername">
            {{ user?.username }}
          </h1>
          <input
            v-else
            ref="usernameInputRef"
            v-model="userUsername"
            type="text"
            class="username-input"
            maxlength="30"
            @blur="saveUsername"
            @keyup.enter="saveUsername"
            @keyup.escape="editingUsername = false; userUsername = user?.username || ''"
          />
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
            加入时间：{{ formatDate(user.createdAt) }}
          </p>
        </div>
      </div>

      <div class="profile-content">
        <section class="section">
          <div class="section-header">
            <div>我的收藏夹</div>
            <router-link to="/favorites" class="see-more">管理收藏 →</router-link>
          </div>
          <div v-if="folders.length > 0" class="folders-grid">
            <div
              v-for="folder in folders"
              :key="folder.id"
              class="folder-card"
              @click="goToFolder(folder.id)"
            >
              <div class="folder-image">
                <img :src="folder.latestAnimeImage || defaultImage" :alt="folder.name" />
              </div>
              <div class="folder-info">
                <h3>{{ folder.name }}</h3>
                <p>{{ folder.count }} 部动漫</p>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <p>还没有创建收藏夹</p>
            <router-link to="/favorites" class="btn btn-primary">去创建</router-link>
          </div>
        </section>

        <section class="section">
      <div class="section-header">
        <h2>观看历史</h2>
        <button v-if="watchHistory.length > 0" class="btn-clear" @click="clearWatchHistory" title="清空所有">
          <el-icon><Brush /></el-icon>
          <span style="margin-left: 5px;">清空</span>
        </button>
      </div>
          <div v-if="watchHistory.length > 0" class="watch-history-list">
            <div class="history-item" v-for="item in watchHistory" :key="item.anime.id">
              <router-link :to="`/watch/${item.episodeId}`" class="history-anime">
                <img :src="item.anime.imageUrl || 'https://via.placeholder.com/80x120/1e293b/475569?text=?'" :alt="item.anime.title" />
                <div class="history-info">
                  <div class="history-header">
                    <h4>{{ item.anime.title }}</h4>
                    <span class="history-time">{{ formatRelativeTime(item.updatedAt) }}</span>
                  </div>
                  <p v-if="item.episodeNumber">第{{ item.episodeNumber }}集</p>
                  <div class="progress-bar-wrapper">
                    <div class="progress-bar">
                      <div class="progress-fill" :style="{ width: (item.progressPercent || 0) + '%' }"></div>
                    </div>
                    <span class="progress-text">{{ item.progressPercent || 0 }}%</span>
                    <span v-if="item.completed" class="badge badge-success">已完成</span>
                  </div>
                  <p class="time-display">{{ formatTime(item.progress) }} / {{ formatTime(item.duration) }}</p>
                </div>
              </router-link>
              <button class="btn-delete" @click.stop="deleteWatchHistory(item.anime.id)" title="删除记录">
                <el-icon><Delete /></el-icon>
              </button>
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
import axios from '../utils/axios'
import { $message } from '../utils/message'
import { Delete, Brush } from '@element-plus/icons-vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import defaultImage from '../assets/favorites/bangumi.png'

const router = useRouter()
const authStore = useAuthStore()

const user = computed(() => authStore.user)
const folders = ref([])
const watchHistory = ref([])
const confirmDialogRef = ref(null)
const userSignature = ref('')
const editingSignature = ref(false)
const signatureInputRef = ref(null)
const userUsername = ref('')
const editingUsername = ref(false)
const usernameInputRef = ref(null)
const avatarUrl = ref('')
const defaultAvatarUrl = '/src/assets/avatars/default.svg'

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN')
}

const formatTime = (seconds) => {
  if (seconds == null || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

const formatRelativeTime = (dateString) => {
  if (!dateString) return ''
  const now = Date.now()
  const then = new Date(dateString).getTime()
  const diff = now - then
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
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

const startEditUsername = async () => {
  userUsername.value = user.value?.username || ''
  editingUsername.value = true
  await nextTick()
  usernameInputRef.value?.focus()
  usernameInputRef.value?.select()
}

const saveUsername = async () => {
  editingUsername.value = false
  const newUsername = userUsername.value?.trim() || ''
  if (!newUsername) {
    $message.error('用户名不能为空')
    return
  }
  if (newUsername === user.value?.username) {
    return
  }
  try {
    const response = await fetch('/api/user/username', {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify({ username: newUsername })
    })
    const data = await response.json()
    if (data.success) {
      if (authStore.user) {
        authStore.user.username = newUsername
      }
      $message.success('用户名修改成功')
    } else {
      $message.error(data.message || '修改失败')
      userUsername.value = user.value?.username || ''
    }
  } catch (error) {
    console.error('Failed to save username:', error)
    $message.error('修改失败，请稍后重试')
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
        $message.success('头像上传成功')
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

watch(() => user.value?.username, (val) => {
  if (!editingUsername.value) {
    userUsername.value = val || ''
  }
}, { immediate: true })

watch(() => user.value?.avatar, () => {
  loadAvatar()
}, { immediate: true })

const fetchFolders = async () => {
  try {
    const response = await axios.get('/user/favorites/folders')
    if (response.data && response.data.data) {
      folders.value = response.data.data
    }
  } catch (error) {
    console.error('Failed to fetch folders:', error)
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

const deleteWatchHistory = async (animeId) => {
  try {
    const userId = authStore.user?.id
    if (!userId) return
    await fetch(`/api/watch-history/user/${userId}/anime/${animeId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${authStore.token}` }
    })
    watchHistory.value = watchHistory.value.filter(i => i.anime.id !== animeId)
  } catch (error) {
    console.error('Failed to delete watch history:', error)
  }
}

const clearWatchHistory = async () => {
  if (!confirmDialogRef.value) return
  const confirmed = await confirmDialogRef.value.show('确定要清空所有观看历史记录吗？此操作不可恢复。')
  if (!confirmed) return
  try {
    const userId = authStore.user?.id
    if (!userId) return
    await fetch(`/api/watch-history/user/${userId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${authStore.token}` }
    })
    watchHistory.value = []
  } catch (error) {
    console.error('Failed to clear watch history:', error)
  }
}

const goToFolder = (folderId) => {
  router.push('/favorites')
}

onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.user) {
    await authStore.verifyToken()
  }
  if (authStore.isAuthenticated) {
    fetchFolders()
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

.username-text {
  cursor: pointer;
  transition: color 0.2s;
  border-bottom: 1px dashed transparent;
}

.username-text:hover {
  color: var(--primary-color);
  border-bottom-color: var(--primary-color);
}

.username-input {
  width: 35ch;
  padding: 0.375rem 0.75rem;
  background: var(--background-dark);
  border: 1px solid var(--primary-color);
  border-radius: 0.5rem;
  color: var(--text-primary);
  font-size: 1.5rem;
  font-weight: 700;
  outline: none;
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

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border-color);
}

.section-header div {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 0;
  border: none;
  padding: 0;
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

.section h2 {
  font-size: 1.125rem;
  font-weight: 600;
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.empty-state p {
  margin-bottom: 1rem;
}

.folders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 180px);
  gap: 1rem;
  justify-content: left;
}

.folder-card {
  background: var(--background-dark);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.folder-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.folder-image {
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--border-color);
}

.folder-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.folder-info {
  padding: 0.75rem;
}

.folder-info h3 {
  color: var(--text-primary);
  font-size: 0.9rem;
  margin: 0 0 0.25rem 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-info p {
  color: var(--text-secondary);
  font-size: 0.75rem;
  margin: 0;
}

.watch-history-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.history-item {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #1a1a2e 100%);
  border-radius: 0.75rem;
  overflow: hidden;
  border: 1px solid rgba(100, 100, 255, 0.1);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.history-item:hover {
  border-color: rgba(100, 100, 255, 0.3);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.history-anime {
  display: flex;
  gap: 1rem;
  padding: 0.75rem 1rem;
  text-decoration: none;
  color: inherit;
}

.history-anime img {
  width: 60px;
  height: 80px;
  border-radius: 0.5rem;
  object-fit: cover;
  flex-shrink: 0;
}

.history-info {
  flex: 1;
  min-width: 0;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.15rem;
}

.history-header h4 {
  font-size: 0.9rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
  flex: 1;
  min-width: 0;
}

.history-time {
  font-size: 0.675rem;
  color: var(--text-muted);
  white-space: nowrap;
  margin-left: 0.5rem;
  flex-shrink: 0;
}

.history-info p {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.3rem;
}

.time-display {
  font-size: 0.6875rem;
  color: var(--text-muted);
  margin-top: 0.25rem;
  margin-bottom: 0;
}

.progress-bar-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--secondary-color));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 0.75rem;
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 35px;
}

.btn-clear {
  padding: 0.375rem 0.75rem;
  font-size: 0.8125rem;
  background: transparent;
  display: flex;
  align-items: center;
  border: 1px solid var(--border-color);
  border-radius: 0.5rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-clear:hover {
  border-color: #ef4444;
  color: #ef4444;
}

.history-item {
  position: relative;
}

.btn-delete {
  position: absolute;
  bottom: 0.5rem;
  right: 0.5rem;
  width: 1.5rem;
  height: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 0.25rem;
  color: var(--text-muted);
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;
  opacity: 0;
}

.history-item:hover .btn-delete {
  opacity: 1;
}

.btn-delete:hover {
  border-color: #ef4444;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.badge-success {
  font-size: 0.625rem;
  padding: 0.125rem 0.4rem;
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
  border: 1px solid rgba(34, 197, 94, 0.3);
  border-radius: 0.25rem;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
  }
}
</style>