<template>
  <div class="favorites-container">
    <div class="sidebar">
      <div class="sidebar-header">
        <h2>收藏夹</h2>
        <button class="btn-create" @click="showCreateDialog = true">+ 新建</button>
      </div>
      
      <div v-if="loadingFolders" class="loading-folders">
        <div class="spinner-small"></div>
      </div>
      
      <div v-else-if="folders.length === 0" class="empty-folders">
        <p>还没有收藏夹</p>
        <button class="btn btn-primary btn-small" @click="showCreateDialog = true">创建收藏夹</button>
      </div>
      
      <div v-else class="folder-list"
        @dragover.prevent="onDragOver"
        @drop="onDrop"
      >
        <div
          v-for="(folder, index) in folders"
          :key="folder.id"
          :class="['folder-item', { active: selectedFolderId === folder.id, dragging: dragIndex === index }]"
          draggable="true"
          @dragstart="onDragStart($event, index)"
          @dragend="onDragEnd"
          @click="selectFolder(folder.id)"
        >
          <div class="folder-thumb">
            <img :src="folder.latestAnimeImage || defaultImage" :alt="folder.name" />
          </div>
          <div class="folder-meta">
            <span class="folder-name">{{ folder.name }}</span>
            <span class="folder-count">{{ folder.count }} 部</span>
          </div>
          <div class="folder-actions" @click.stop>
            <button class="action-btn" @click.stop="toggleFolderMenu(folder, $event)">⋮</button>
            <div v-if="activeFolderMenu === folder.id" class="dropdown-menu folder-dropdown" :style="dropdownStyle">
              <button class="dropdown-item" @click.stop="renameFolder(folder)">✏️ 重命名</button>
              <button class="dropdown-item danger" @click.stop="deleteFolder(folder.id, folder.name)">🗑️ 删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="content">
      <div v-if="!selectedFolderId" class="empty-content">
        <div class="empty-icon">📁</div>
        <h2>选择一个收藏夹</h2>
        <p>从左侧选择收藏夹查看其中的番剧</p>
      </div>
      
      <div v-else-if="loadingAnimes" class="loading">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="animes.length === 0" class="empty-content">
        <div class="empty-icon">📭</div>
        <h2>收藏夹是空的</h2>
        <p>去浏览页添加一些番剧吧！</p>
        <router-link to="/browse" class="btn btn-primary">去浏览</router-link>
      </div>
      
      <div v-else class="anime-section">
        <div class="section-header">
          <h2>{{ selectedFolderName }}</h2>
          <span class="anime-count">{{ animes.length }} 部番剧</span>
        </div>
        <div class="anime-grid">
          <div
            v-for="anime in animes"
            :key="anime.id"
            class="anime-card"
            @click="viewAnime(anime.id)"
          >
            <div class="anime-poster">
              <img :src="anime.imageUrl" :alt="anime.title" />
              <div class="anime-score">{{ anime.score }}</div>
            </div>
            <div class="anime-info">
              <div class="anime-title-row">
                <h3>{{ anime.title }}</h3>
                <div class="anime-actions-wrapper">
                  <button class="more-btn">⋮</button>
                  <div class="dropdown-menu anime-dropdown">
                    <button class="dropdown-item" @click.stop="copyToFolder(anime)">📋 复制至收藏夹</button>
                    <button class="dropdown-item" @click.stop="moveToFolder(anime)">📁 移动至收藏夹</button>
                    <button class="dropdown-item danger" @click.stop="removeFromFolder(anime.id)">☆ &nbsp;取消收藏</button>
                  </div>
                </div>
              </div>
              <p class="anime-title-jp">{{ anime.titleJp }}</p>
              <div class="anime-meta">
                <span>{{ anime.type }}</span>
                <span>{{ anime.episodes }}集</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showCreateDialog" class="dialog-overlay" @click="showCreateDialog = false">
      <div class="dialog" @click.stop>
        <h2>新建收藏夹</h2>
        <input
          v-model="newFolderName"
          type="text"
          placeholder="输入收藏夹名称"
          @keyup.enter="createFolder"
        />
        <div v-if="createError" class="error-message">{{ createError }}</div>
        <div class="dialog-actions">
          <button class="btn btn-secondary" @click="showCreateDialog = false">取消</button>
          <button class="btn btn-primary" @click="createFolder" :disabled="!newFolderName.trim()">
            创建
          </button>
        </div>
      </div>
    </div>

    <div v-if="showMoveDialog" class="dialog-overlay" @click="closeMoveDialog">
      <div class="dialog" @click.stop>
        <h2>{{ isCopyOperation ? '复制至收藏夹' : '移动至收藏夹' }}</h2>
        <div v-if="folderListForMove.length === 0" class="empty-folders">
          <p>还没有其他收藏夹</p>
        </div>
        <div v-else class="folder-select-list">
          <div
            v-for="folder in folderListForMove"
            :key="folder.id"
            :class="['folder-select-item', { disabled: folder.id === selectedFolderId }]"
            @click="folder.id !== selectedFolderId && selectTargetFolder(folder)"
          >
            <div class="folder-thumb">
              <img :src="folder.latestAnimeImage || defaultImage" :alt="folder.name" />
            </div>
            <div class="folder-meta">
              <span class="folder-name">{{ folder.name }}</span>
              <span class="folder-count">{{ folder.count }} 部</span>
            </div>
            <span v-if="folder.id === selectedFolderId" class="current-badge">当前</span>
          </div>
        </div>
        <div v-if="moveError" class="error-message">{{ moveError }}</div>
        <div class="dialog-actions">
          <button class="btn btn-secondary" @click="closeMoveDialog">取消</button>
          <button class="btn btn-primary" @click="confirmMove" :disabled="!targetFolderId">
            {{ isCopyOperation ? '复制' : '移动' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="showRenameDialog" class="dialog-overlay" @click="showRenameDialog = false">
      <div class="dialog" @click.stop>
        <h2>重命名收藏夹</h2>
        <input
          v-model="renameFolderName"
          type="text"
          placeholder="输入新名称"
          @keyup.enter="confirmRename"
        />
        <div v-if="renameError" class="error-message">{{ renameError }}</div>
        <div class="dialog-actions">
          <button class="btn btn-secondary" @click="showRenameDialog = false">取消</button>
          <button class="btn btn-primary" @click="confirmRename" :disabled="!renameFolderName.trim()">
            保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from '../utils/axios'
import { useAuthStore } from '../stores/auth'
import $confirm from '../utils/confirm'
import { $message } from '../utils/message'
import defaultImage from '../assets/favorites/bangumi.png'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const folders = ref([])
const animes = ref([])
const selectedFolderId = ref(null)
const selectedFolderName = ref('')
const loadingFolders = ref(false)
const loadingAnimes = ref(false)
const showCreateDialog = ref(false)
const newFolderName = ref('')
const createError = ref(null)

const dragIndex = ref(-1)
const dragOverIndex = ref(-1)
const activeFolderMenu = ref(null)
const dropdownStyle = ref({})

const showMoveDialog = ref(false)
const showRenameDialog = ref(false)
const isCopyOperation = ref(false)
const targetFolderId = ref(null)
const moveAnime = ref(null)
const folderListForMove = ref([])
const moveError = ref(null)
const renameFolderName = ref('')
const renameError = ref(null)
const folderToRename = ref(null)

const toggleFolderMenu = (folder, event) => {
  event.stopPropagation()
  const button = event.currentTarget

  if (activeFolderMenu.value === folder.id) {
    activeFolderMenu.value = null
  } else {
    activeFolderMenu.value = folder.id
    const rect = button.getBoundingClientRect()
    dropdownStyle.value = {
      position: 'fixed',
      top: `${rect.bottom + window.scrollY + 5}px`,
      left: `${rect.left + window.scrollX}px`
    }
  }
}

const closeAllMenus = (event) => {
  if (event) {
    const target = event.target
    if (target.closest('.dropdown-menu') || target.closest('.folder-actions')) {
      return
    }
  }
  activeFolderMenu.value = null
}

const onDragStart = (event, index) => {
  dragIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', index)
}

const onDragOver = (event) => {
  event.preventDefault()
  const folderList = event.currentTarget
  const items = folderList.querySelectorAll('.folder-item')
  let newIndex = 0

  for (let i = 0; i < items.length; i++) {
    const rect = items[i].getBoundingClientRect()
    if (event.clientY < rect.bottom) {
      newIndex = i
      break
    }
    if (i === items.length - 1) {
      newIndex = items.length
    }
  }

  dragOverIndex.value = newIndex
}

const onDrop = async (event) => {
  event.preventDefault()
  const fromIndex = dragIndex.value
  let toIndex = dragOverIndex.value

  if (fromIndex === -1 || toIndex === -1) {
    dragIndex.value = -1
    dragOverIndex.value = -1
    return
  }

  if (fromIndex === toIndex) {
    dragIndex.value = -1
    dragOverIndex.value = -1
    return
  }

  const foldersCopy = [...folders.value]
  const [movedItem] = foldersCopy.splice(fromIndex, 1)

  let insertIndex = toIndex
  if (fromIndex < toIndex) {
    insertIndex = toIndex - 1
  }

  if (insertIndex >= foldersCopy.length) {
    foldersCopy.push(movedItem)
  } else {
    foldersCopy.splice(insertIndex, 0, movedItem)
  }

  folders.value = foldersCopy

  try {
    const folderOrder = folders.value.map(f => f.id)
    await axios.put('/user/favorites/folders/reorder', { folderOrder })
  } catch (err) {
    console.error('Failed to reorder folders:', err)
    await fetchFolders()
  }

  dragIndex.value = -1
  dragOverIndex.value = -1
}

const onDragEnd = () => {
  dragIndex.value = -1
  dragOverIndex.value = -1
}

const fetchFolders = async () => {
  loadingFolders.value = true
  try {
    const response = await axios.get('/user/favorites/folders')
    if (response.data && response.data.data) {
      folders.value = response.data.data
      if (folders.value.length > 0 && !selectedFolderId.value) {
        selectFolder(folders.value[0].id)
      }
    }
  } catch (err) {
    console.error('Failed to fetch folders:', err)
  } finally {
    loadingFolders.value = false
  }
}

const selectFolder = (folderId) => {
  selectedFolderId.value = folderId
  const folder = folders.value.find(f => f.id === folderId)
  if (folder) {
    selectedFolderName.value = folder.name
  }
  fetchAnimes(folderId)
}

const fetchAnimes = async (folderId) => {
  if (!folderId) return

  loadingAnimes.value = true
  try {
    const response = await axios.get(`/user/favorites/folders/${folderId}`)
    if (response.data && response.data.data) {
      animes.value = response.data.data
    } else {
      animes.value = []
    }
  } catch (err) {
    console.error('Failed to fetch animes:', err)
    animes.value = []
  } finally {
    loadingAnimes.value = false
  }
}

const createFolder = async () => {
  if (!newFolderName.value.trim()) return

  createError.value = null
  try {
    const response = await axios.post('/user/favorites/folders', { name: newFolderName.value.trim() })
    showCreateDialog.value = false
    newFolderName.value = ''
    await fetchFolders()
    if (response.data && response.data.data) {
      selectFolder(response.data.data.id)
    }
    $message.success('收藏夹创建成功')
  } catch (err) {
    createError.value = err.response?.data?.message || '创建收藏夹失败'
  }
}

const deleteFolder = async (folderId, folderName) => {
  activeFolderMenu.value = null
  
  const confirmed = await $confirm(`确定删除这个收藏夹吗？`)
  if (!confirmed) {
    return
  }
  
  try {
    await axios.delete(`/user/favorites/folders/${folderId}`)
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
      animes.value = []
    }
    fetchFolders()
    $message.success('收藏夹已删除')
  } catch (err) {
    console.error('Failed to delete folder:', err)
  }
}

const renameFolder = (folder) => {
  activeFolderMenu.value = null
  folderToRename.value = folder
  renameFolderName.value = folder.name
  renameError.value = null
  showRenameDialog.value = true
}

const confirmRename = async () => {
  if (!renameFolderName.value.trim() || !folderToRename.value) return

  renameError.value = null
  try {
    await axios.put(`/user/favorites/folders/${folderToRename.value.id}`, { name: renameFolderName.value.trim() })
    showRenameDialog.value = false
    folderToRename.value = null
    renameFolderName.value = ''
    await fetchFolders()
  } catch (err) {
    renameError.value = err.response?.data?.message || '重命名失败'
  }
}

const copyToFolder = (anime) => {
  isCopyOperation.value = true
  moveAnime.value = anime
  folderListForMove.value = folders.value.filter(f => f.id !== selectedFolderId.value)
  targetFolderId.value = null
  moveError.value = null
  showMoveDialog.value = true
}

const moveToFolder = (anime) => {
  isCopyOperation.value = false
  moveAnime.value = anime
  folderListForMove.value = folders.value.filter(f => f.id !== selectedFolderId.value)
  targetFolderId.value = null
  moveError.value = null
  showMoveDialog.value = true
}

const selectTargetFolder = (folder) => {
  targetFolderId.value = folder.id
}

const closeMoveDialog = () => {
  showMoveDialog.value = false
  moveAnime.value = null
  targetFolderId.value = null
  folderListForMove.value = []
}

const confirmMove = async () => {
  if (!targetFolderId.value || !moveAnime.value) return

  moveError.value = null
  try {
    if (isCopyOperation.value) {
      await axios.post(`/user/favorites/folders/${targetFolderId.value}/anime/${moveAnime.value.id}`)
      $message.success('已复制到目标收藏夹')
    } else {
      await axios.delete(`/user/favorites/folders/${selectedFolderId.value}/anime/${moveAnime.value.id}`)
      await axios.post(`/user/favorites/folders/${targetFolderId.value}/anime/${moveAnime.value.id}`)
      $message.success('已移动到目标收藏夹')
    }
    closeMoveDialog()
    fetchAnimes(selectedFolderId.value)
    fetchFolders()
  } catch (err) {
    moveError.value = err.response?.data?.message || '操作失败'
  }
}

const removeFromFolder = async (animeId) => {
  if (!selectedFolderId.value) return

  try {
    await axios.delete(`/user/favorites/folders/${selectedFolderId.value}/anime/${animeId}`)
    fetchAnimes(selectedFolderId.value)
    fetchFolders()
  } catch (err) {
    console.error('Failed to remove from folder:', err)
  }
}

const viewAnime = (animeId) => {
  router.push(`/anime/${animeId}`)
}

onMounted(() => {
  if (authStore.isAuthenticated) {
    fetchFolders()
  }
  document.addEventListener('click', closeAllMenus)
})

onUnmounted(() => {
  document.removeEventListener('click', closeAllMenus)
})
</script>

<style scoped>
.favorites-container {
  display: flex;
  min-height: calc(100vh - 8rem);
  max-width: 1400px;
  margin: 0 auto;
  padding: 1.5rem;
  gap: 1.5rem;
}

.sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--background-light);
  border-radius: 12px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 1rem;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 1.125rem;
  color: var(--text-primary);
}

.btn-create {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: white;
  border: none;
  padding: 0.375rem 0.75rem;
  border-radius: 6px;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: none;
}

.btn-create:hover {
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  transform: translateY(-1px);
}

.loading-folders {
  display: flex;
  justify-content: center;
  padding: 2rem;
}

.spinner-small {
  width: 24px;
  height: 24px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.empty-folders {
  text-align: center;
  padding: 2rem 1rem;
  color: var(--text-secondary);
}

.empty-folders p {
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.folder-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  overflow-y: auto;
  flex: 1;
}

.folder-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.2s;
  position: relative;
}

.folder-item:hover {
  background: rgba(99, 102, 241, 0.1);
}

.folder-item.active {
  background: rgba(99, 102, 241, 0.2);
  border: 1px solid rgba(99, 102, 241, 0.3);
}

.folder-item.dragging {
  opacity: 0.5;
  background: rgba(99, 102, 241, 0.3);
}

.folder-item:active {
  cursor: grabbing;
}

.folder-thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--border-color);
}

.folder-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.folder-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.folder-name {
  color: var(--text-primary);
  font-size: 0.875rem;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-count {
  color: var(--text-secondary);
  font-size: 0.75rem;
}

.folder-actions {
  position: relative;
}

.action-btn {
  background: none;
  border: none;
  font-size: 1.25rem;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}

.action-btn:hover {
  background: rgba(99, 102, 241, 0.2);
  color: var(--text-primary);
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 0.625rem 1rem;
  background: none;
  border: none;
  text-align: left;
  color: var(--text-primary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: background 0.2s;
}

.dropdown-item:hover {
  background: rgba(99, 102, 241, 0.15);
}

.dropdown-item.danger {
  color: #f87171;
}

.dropdown-item.danger:hover {
  background: rgba(248, 113, 113, 0.15);
}

.content {
  flex: 1;
  background: var(--background-light);
  border-radius: 12px;
  padding: 1.5rem;
  min-height: 500px;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 400px;
  text-align: center;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.empty-content h2 {
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.empty-content p {
  color: var(--text-secondary);
  margin-bottom: 1.5rem;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 400px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
}

.section-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.25rem;
}

.anime-count {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.anime-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 1.5rem;
  justify-content: left;
}

.anime-card {
  background: var(--background-dark);
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.anime-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.anime-poster {
  position: relative;
  aspect-ratio: 3/4;
  overflow: hidden;
  border-radius: 8px 8px 0 0;
}

.anime-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.anime-score {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.8);
  color: #ffd700;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: bold;
}

.anime-info {
  padding: 12px;
  position: relative;
  background: var(--background-dark);
}

.anime-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 4px;
}

.anime-title-row h3 {
  flex: 1;
  color: var(--text-primary);
  font-size: 1rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0;
}

.anime-actions-wrapper {
  position: relative;
  flex-shrink: 0;
}

.more-btn {
  background: transparent;
  border: none;
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 1.25rem;
  cursor: pointer;
  padding: 4px 8px;
  transition: all 0.2s;
}

.more-btn:hover {
  background: rgba(99, 102, 241, 0.2);
  color: var(--primary-color);
}

.dropdown-menu {
  display: none;
  position: absolute;
  top: 100%;
  right: 0;
  background: var(--background-dark);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.5rem 0;
  width: fit-content;
  min-width: 140px;
  max-width: 200px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  z-index: 10000;
}

.anime-actions-wrapper:hover .anime-dropdown {
  display: block;
}

.folder-dropdown {
  display: block;
}

.anime-title-jp {
  color: var(--text-muted);
  font-size: 0.875rem;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.anime-meta {
  display: flex;
  gap: 8px;
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.anime-meta span {
  background: var(--border-color);
  padding: 2px 8px;
  border-radius: 4px;
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: linear-gradient(145deg, #1e293b 0%, #0f172a 100%);
  padding: 2rem;
  border-radius: 16px;
  width: 100%;
  max-width: 400px;
  box-shadow: 
    0 0 0 1px rgba(99, 102, 241, 0.3),
    0 20px 60px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.dialog h2 {
  margin: 0 0 1.5rem 0;
  color: #f1f5f9;
  font-size: 1.25rem;
}

.dialog input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid rgba(71, 85, 105, 0.6);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.8);
  color: #e2e8f0;
  font-size: 1rem;
  box-sizing: border-box;
}

.dialog input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
}

.error-message {
  color: #f87171;
  font-size: 0.875rem;
  margin-top: 0.75rem;
}

.dialog-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  margin-top: 1.5rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.625rem 1.25rem;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.875rem;
  transition: all 0.2s ease;
  box-shadow: none;
  transform: translateY(0);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
}

.btn-primary:hover:not(:disabled),
.btn-primary:not(:disabled):hover,
a.btn-primary:hover {
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  transform: translateY(-1px);
}

.btn-secondary {
  background: var(--border-color);
  color: var(--text-primary);
}

.btn-secondary:hover {
  background: var(--surface-color);
}

.btn-small {
  padding: 0.5rem 1rem;
  font-size: 0.8125rem;
}

.folder-select-list {
  max-height: 300px;
  overflow-y: auto;
  margin: 1rem 0;
}

.folder-select-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.folder-select-item:hover:not(.disabled) {
  background: rgba(99, 102, 241, 0.1);
}

.folder-select-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.current-badge {
  position: absolute;
  right: 1rem;
  background: var(--primary-color);
  color: white;
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 10px;
}

@media (max-width: 768px) {
  .favorites-container {
    flex-direction: column;
    padding: 1rem;
  }
  
  .sidebar {
    width: 100%;
    max-height: 300px;
  }
  
  .content {
    min-height: 400px;
  }
}
</style>
