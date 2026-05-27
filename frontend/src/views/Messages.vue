<template>
  <div class="messages-page">
    <div class="messages-container">
      <div class="messages-header">
        <h1>消息中心</h1>
        <button @click="markAllAsRead" class="btn btn-primary" v-if="filteredMessages.filter(m => !m.isRead).length > 0">
          全部已读
        </button>
      </div>

      <div class="filter-tabs">
        <button 
          @click="activeTab = 'all'" 
          class="filter-tab"
          :class="{ 'active': activeTab === 'all' }"
        >
          全部消息 ({{ messages.length }})
        </button>
        <button 
          @click="activeTab = 'unread'" 
          class="filter-tab"
          :class="{ 'active': activeTab === 'unread' }"
        >
          未读消息 ({{ messages.filter(m => !m.isRead).length }})
        </button>
      </div>

      <div class="messages-list" v-if="filteredMessages.length > 0">
        <div 
          v-for="message in filteredMessages" 
          :key="message.id" 
          class="message-item"
          :class="{ 'unread': !message.isRead }"
          @click="markAsRead(message.id)"
        >
          <div class="message-icon">
            <span v-if="message.type === 'ADMIN_ANNOUNCEMENT'">📢</span>
            <span v-else-if="message.type === 'REVIEW_REPLY'">💬</span>
            <span v-else-if="message.type === 'REVIEW_LIKE'">❤️</span>
            <span v-else-if="message.type === 'REVIEW_LIKE_CANCELLED'">💔</span>
            <span v-else>📩</span>
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-title">{{ message.title }}</span>
              <span class="message-time">{{ formatDate(message.createdAt) }}</span>
            </div>
            <p class="message-body">{{ message.content }}</p>
            <div v-if="message.relatedUsername" class="message-meta">
              来自: {{ message.relatedUsername }}
            </div>
          </div>
          <button @click.stop="deleteMessage(message.id)" class="delete-btn">✕</button>
        </div>
      </div>

      <div v-else class="empty-state">
        <span class="empty-icon">📭</span>
        <p>{{ activeTab === 'unread' ? '暂无未读消息' : '暂无消息' }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from '../utils/axios'
import { $message } from '../utils/message'
import { eventBus, MESSAGE_EVENTS } from '../utils/eventBus'

const messages = ref([])
const activeTab = ref('all')

const filteredMessages = computed(() => {
  if (activeTab.value === 'unread') {
    return messages.value.filter(m => !m.isRead)
  }
  return messages.value
})

const fetchMessages = async () => {
  try {
    const response = await axios.get('/user/messages')
    console.log('Messages response:', response)
    if (response.data && response.data.success) {
      messages.value = response.data.data
    }
  } catch (error) {
    console.error('Failed to fetch messages:', error)
  }
}

const markAsRead = async (messageId) => {
  try {
    await axios.put(`/user/messages/${messageId}/read`)
    const message = messages.value.find(m => m.id === messageId)
    if (message) {
      message.isRead = true
    }
    eventBus.emit(MESSAGE_EVENTS.UNREAD_COUNT_CHANGED)
  } catch (error) {
    console.error('Failed to mark as read:', error)
  }
}

const markAllAsRead = async () => {
  try {
    await axios.put('/user/messages/read-all')
    messages.value.forEach(m => m.isRead = true)
    $message.success('全部已读')
    eventBus.emit(MESSAGE_EVENTS.UNREAD_COUNT_CHANGED)
  } catch (error) {
    console.error('Failed to mark all as read:', error)
  }
}

const deleteMessage = async (messageId) => {
  if (!confirm('确定要删除这条消息吗？')) return
  try {
    await axios.delete(`/user/messages/${messageId}`)
    messages.value = messages.value.filter(m => m.id !== messageId)
    $message.success('删除成功')
    eventBus.emit(MESSAGE_EVENTS.UNREAD_COUNT_CHANGED)
  } catch (error) {
    console.error('Failed to delete message:', error)
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  fetchMessages()
})
</script>

<style scoped>
.messages-page {
  min-height: calc(100vh - 5rem);
  padding: 2rem;
  background: var(--background);
}

.messages-container {
  max-width: 800px;
  margin: 0 auto;
}

.messages-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.messages-header h1 {
  font-size: 1.75rem;
  color: var(--text-primary);
}

.filter-tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.filter-tab {
  padding: 0.75rem 1.25rem;
  background: var(--background-light);
  border: 1px solid var(--border-color);
  border-radius: 0.5rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-tab:hover {
  background: var(--surface-color);
}

.filter-tab.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem;
  background: var(--background-light);
  border-radius: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.message-item:hover {
  background: var(--surface-color);
  border-color: var(--border-color);
}

.message-item.unread {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1), rgba(168, 85, 247, 0.05));
  border-color: rgba(99, 102, 241, 0.3);
}

.message-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.message-title {
  font-weight: 600;
  color: var(--text-primary);
}

.message-time {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.message-body {
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

.message-meta {
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.delete-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 0.25rem;
  transition: all 0.2s;
}

.delete-btn:hover {
  background: var(--error-color);
  color: white;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.empty-state p {
  color: var(--text-muted);
  font-size: 1rem;
}
</style>