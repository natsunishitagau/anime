<template>
  <div class="agent-chat-page">
    <div class="chat-container">
      <div class="chat-sidebar">
        <div class="sidebar-header">
          <h2>智能助手</h2>
          <button @click="createNewChat" class="btn btn-primary btn-sm">
            + 新对话
          </button>
        </div>
        
        <div class="chat-list" v-if="filteredChatList.length > 0">
          <div 
            v-for="chat in filteredChatList" 
            :key="chat.thread_id"
            class="chat-item"
            :class="{ active: currentThreadId === chat.thread_id }"
            @click="switchChat(chat.thread_id)"
          >
            <div class="chat-info">
              <div class="chat-title">{{ chat.title }}</div>
            </div>
            <button 
              @click.stop="deleteChat(chat.thread_id)" 
              class="delete-chat-btn"
              title="删除对话"
            >✕</button>
          </div>
        </div>

        <div v-else class="empty-chat-list">
          <span class="empty-icon">💬</span>
          <p>暂无对话</p>
        </div>
      </div>

      <div class="chat-main">
        <div v-if="currentThreadId" class="chat-content">
          <div class="chat-header">
            <div class="header-info">
              <img src="/src/assets/avatars/anime-agent.png" class="agent-avatar-img" />
              <span class="agent-name">Anime Master</span>
            </div>
          </div>

          <div v-if="messages.length > 0" ref="messagesContainer" class="messages-container">
            <div 
              v-for="(message, index) in messages" 
              :key="index"
              class="message-wrapper"
              :class="{ 
                'user-message': message.role === 'user', 
                'assistant-message': message.role === 'assistant',
                'tool-message': message.role === 'tool_call',
                'consider-message': message.role === 'consider'
              }"
            >
              <template v-if="message.role === 'tool_call'">
                <div class="tool-indicator tool-call">
                  <span class="tool-spinner"></span>
                  <span class="tool-text">正在 <strong>{{ message.tool }}</strong></span>
                </div>
              </template>
              <template v-else-if="message.role === 'consider'">
                <div class="message-avatar user-avatar">
                  <img src="/src/assets/avatars/anime-agent.png" alt="Anime Agent" class="agent-avatar-img" />
                </div>
                <div class="message-content">
                  <div class="message-text consider-text">
                    <span class="consider-icon">💭</span>
                    <span class="consider-label">思考中...</span>
                    <span v-html="md.render(message.content)"></span>
                  </div>
                </div>
              </template>
              <template v-else-if="message.content!=''">
                <div class="message-avatar">
                  <span v-if="message.role === 'user' && !currentUser?.avatarUrl">👤</span>
                  <img v-else-if="message.role === 'user' && currentUser?.avatarUrl" :src="currentUser.avatarUrl" alt="User" class="agent-avatar-img" />
                  <img v-else src="/src/assets/avatars/anime-agent.png" alt="Anime Agent" class="agent-avatar-img" />
                </div>
                <div class="message-content">
                  <div v-if="message.role === 'user'" class="message-text">{{ message.content }}</div>
                  <div v-else class="message-text" v-html="md.render(message.content)"></div>
                </div>
              </template>
            </div>
            
            <div v-if="isLoading" class="loading-indicator">
              <span class="typing-dots">
                <span></span>
                <span></span>
                <span></span>
              </span>
            </div>
          </div>
          
          <div v-else-if="isLoading" class="empty-loading-state">
            <div class="loading-indicator">
              <span class="typing-dots">
                <span></span>
                <span></span>
                <span></span>
              </span>
            </div>
          </div>
          
          <div v-else class="empty-chat-state">
            <span class="empty-chat-icon">💬</span>
            <p>开始与 Anime Master 对话</p>
            <p class="empty-chat-hint">输入消息开始新的对话</p>
          </div>
        </div>

        <div v-else class="welcome-screen">
          <img src="/src/assets/avatars/Master.svg" class="welcome-icon" />
          <h1>Anime Master</h1>
          <p>你的专属动漫智能助手</p>
          <div class="features">
            <div class="feature">
              <span class="feature-icon">📚</span>
              <span>动漫推荐</span>
            </div>
            <div class="feature">
              <span class="feature-icon">🎯</span>
              <span>剧情分析</span>
            </div>
            <div class="feature">
              <span class="feature-icon">💬</span>
              <span>角色讨论</span>
            </div>
          </div>
        </div>

        <div class="input-container">
          <textarea
            v-model="inputMessage"
            @keydown.enter.exact.prevent="sendMessage"
            placeholder="输入消息..."
            class="message-input"
            rows="2"
            :disabled="isLoading"
          ></textarea>
          <button 
            @click="sendMessage" 
            class="send-btn"
            :disabled="!inputMessage.trim() || isLoading"
          >
            <span>发送</span>
            <span class="send-icon">➤</span>
          </button>
        </div>
      </div>
    </div>
  </div>
  
  <ConfirmDialog ref="confirmDialogRef" />
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { $message } from '../utils/message'
import { useAuthStore } from '../stores/auth'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const AGENT_API_BASE = 'http://localhost:8000'

const authStore = useAuthStore()
const currentUserId = computed(() => authStore.currentUser?.id || 1)
const currentUser = computed(() => authStore.currentUser)

const chatList = ref([])
const currentThreadId = ref('')
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const messagesContainer = ref(null)
const isNewChat = ref(true)
const confirmDialogRef = ref(null)

let activeRequest = null
let activeReader = null
let activeTypingInterval = null

const cancelActiveRequest = () => {
  if (activeReader) {
    try {
      activeReader.cancel()
    } catch (e) {
      console.warn('Failed to cancel reader:', e)
    }
    activeReader = null
  }
  if (activeTypingInterval) {
    clearInterval(activeTypingInterval)
    activeTypingInterval = null
  }
  activeRequest = null
}

const filteredChatList = computed(() => {
  return chatList.value.filter(chat => chat.title && chat.title.trim())
})

const fetchChatList = async () => {
  try {
    const response = await fetch(`${AGENT_API_BASE}/api/chat/threads/${currentUserId.value}`)
    if (response.ok) {
      const data = await response.json()
      chatList.value = data.map(item => ({
        ...item
      }))
    }
  } catch (error) {
    console.error('Failed to fetch chat list:', error)
  }
}

const fetchHistory = async (threadId) => {
  try {
    const response = await fetch(`${AGENT_API_BASE}/api/history/${threadId}`)
    if (response.ok) {
      const data = await response.json()
      messages.value = data.messages
    } else if (response.status === 404) {
      messages.value = []
    }
  } catch (error) {
    console.error('Failed to fetch history:', error)
    messages.value = []
  }
}

const createNewChat = () => {
  cancelActiveRequest()
  currentThreadId.value = ''
  messages.value = []
  inputMessage.value = ''
  isNewChat.value = true
  nextTick(() => {
    focusInput()
  })
}

const switchChat = async (threadId) => {
  cancelActiveRequest()
  currentThreadId.value = threadId
  isNewChat.value = false
  await fetchHistory(threadId)
  isLoading.value = false
  nextTick(() => {
    scrollToBottom()
  })
}

const deleteChat = async (threadId) => {
  const confirmed = await confirmDialogRef.value.show('确定要删除这个对话吗？')
  if (!confirmed) return
  
  try {
    const response = await fetch(`${AGENT_API_BASE}/api/history/${threadId}`, {
      method: 'DELETE'
    })
    if (response.ok) {
      chatList.value = chatList.value.filter(c => c.thread_id !== threadId)
      if (currentThreadId.value === threadId) {
        currentThreadId.value = ''
        messages.value = []
      }
      $message.success('对话已删除')
    } else {
      $message.error('删除失败')
    }
  } catch (error) {
    console.error('Failed to delete chat:', error)
    $message.error('删除失败')
  }
}

const scrollToBottom = (smooth = true) => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'auto'
    })
  }
}

const focusInput = () => {
  const input = document.querySelector('.message-input')
  if (input) {
    input.focus()
  }
}

const sendMessage = async () => {
  const message = inputMessage.value.trim()
  if (!message || isLoading.value) return

  isLoading.value = true
  const userMessage = { role: 'user', content: message }
  messages.value.push(userMessage)
  
  inputMessage.value = ''
  
  nextTick(() => {
    scrollToBottom(true)
  })

  try {
    let threadId = currentThreadId.value
    
    if (isNewChat.value) {
      const threadIdResponse = await fetch(`${AGENT_API_BASE}/api/chat/generate-thread-id`)
      if (!threadIdResponse.ok) {
        throw new Error('Failed to generate thread id')
      }
      const threadIdData = await threadIdResponse.json()
      threadId = threadIdData.thread_id
      
      currentThreadId.value = threadId
      isNewChat.value = false
      
      chatList.value.unshift({
        thread_id: threadId,
        title: '新对话',
        updated_at: new Date().toISOString()
      })
      
      fetch(`${AGENT_API_BASE}/api/chat/new`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          user_id: currentUserId.value,
          message: message,
          thread_id: threadId
        })
      }).then(async (newChatResponse) => {
        if (newChatResponse.ok) {
          const newChatData = await newChatResponse.json()
          const chatIndex = chatList.value.findIndex(c => c.thread_id === threadId)
          if (chatIndex >= 0) {
            chatList.value[chatIndex].title = newChatData.title
          }
        }
      }).catch(err => {
        console.error('Failed to create chat:', err)
      })
    }

    const response = await fetch(`${AGENT_API_BASE}/api/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: message,
        thread_id: threadId
      })
    })

    if (!response.ok) {
      throw new Error('Failed to send message')
    }

    const currentRequestThreadId = threadId
    activeRequest = response
    const reader = response.body.getReader()
    activeReader = reader
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    const assistantContent = ref('')
    const assistantMessage = { role: 'assistant', get content() { return assistantContent.value }, set content(val) { assistantContent.value = val } }
    let considerMessage = null
    let toolCallMessage = null
    let hasContent = false
    let contentQueue = ''
    let typingInterval = null
    const TYPING_SPEED = 50
    const CHARS_PER_TICK = 3
    messages.value.push(assistantMessage)

    const startTyping = () => {
      if (typingInterval) return
      typingInterval = setInterval(() => {
        if (currentThreadId.value !== currentRequestThreadId) {
          stopTyping()
          return
        }
        if (contentQueue.length > 0) {
          const charsToAdd = contentQueue.slice(0, CHARS_PER_TICK)
          contentQueue = contentQueue.slice(CHARS_PER_TICK)
          assistantContent.value += charsToAdd
          nextTick(() => {
            scrollToBottom(false)
          })
        }
      }, TYPING_SPEED)
      activeTypingInterval = typingInterval
    }

    const stopTyping = () => {
      if (typingInterval) {
        clearInterval(typingInterval)
        typingInterval = null
        activeTypingInterval = null
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      
      if (currentThreadId.value !== currentRequestThreadId) {
        stopTyping()
        break
      }
      
      if (done) {
        if (buffer.trim()) {
          console.warn('Remaining buffer after stream ended:', buffer)
        }
        stopTyping()
        while (contentQueue.length > 0 && currentThreadId.value === currentRequestThreadId) {
          const charsToAdd = contentQueue.slice(0, CHARS_PER_TICK)
          contentQueue = contentQueue.slice(CHARS_PER_TICK)
          assistantContent.value += charsToAdd
          await new Promise(resolve => setTimeout(resolve, TYPING_SPEED))
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })
      
      while (true) {
        if (currentThreadId.value !== currentRequestThreadId) {
          break
        }
        
        const separatorIndex = buffer.indexOf('\n\n')
        if (separatorIndex === -1) {
          break
        }

        const eventData = buffer.substring(0, separatorIndex)
        buffer = buffer.substring(separatorIndex + 2)

        const trimmedEvent = eventData.trim()
        if (!trimmedEvent || !trimmedEvent.startsWith('data: ')) {
          continue
        }

        try {
              const data = JSON.parse(trimmedEvent.substring(5))
              if (data.type === 'content') {
                contentQueue += data.content

                if (!hasContent) {
                  hasContent = true
                  messages.value = messages.value.filter(msg => 
                    msg.role !== 'consider' && msg.role !== 'tool_call'
                  )
                  startTyping()
                }

              } else if (data.type === 'consider' && !hasContent) {
                if (!considerMessage) {
                  considerMessage = { role: 'consider', content: '' }
                  messages.value.push(considerMessage)
                  nextTick(() => {
                    scrollToBottom(false)
                  })
                  isLoading.value = false
                }
              } else if (data.type === 'tool_call' && !hasContent) {
                if (!toolCallMessage) {
                  toolCallMessage = { role: 'tool_call', tool: data.content }
                  messages.value.push(toolCallMessage)
                  nextTick(() => {
                    scrollToBottom(false)
                  })
                }
              } else if (data.type === 'done') {
                stopTyping()
                while (contentQueue.length > 0 && currentThreadId.value === currentRequestThreadId) {
                  const charsToAdd = contentQueue.slice(0, CHARS_PER_TICK)
                  contentQueue = contentQueue.slice(CHARS_PER_TICK)
                  assistantContent.value += charsToAdd
                  await new Promise(resolve => setTimeout(resolve, TYPING_SPEED))
                }
                const chatIndex = chatList.value.findIndex(c => c.thread_id === threadId)
                if (chatIndex >= 0) {
                  chatList.value[chatIndex] = {
                    ...chatList.value[chatIndex],
                    updated_at: new Date().toISOString()
                  }
                }
                break
              }
            } catch (e) {
          console.warn('Failed to parse SSE data:', trimmedEvent, e)
        }
      }
      
      if (assistantMessage.role === 'done') {
        stopTyping()
        break
      }
    }
  } catch (error) {
    console.error('Error during streaming:', error)
    $message.error('消息发送失败，请重试')
    messages.value = messages.value.slice(0, -1)
    if (isNewChat.value && messages.value.length === 0) {
      currentThreadId.value = ''
    }
  } finally {
    isLoading.value = false
    nextTick(() => {
      scrollToBottom(false)
      focusInput()
    })
  }
}

watch(messages, () => {
  nextTick(() => scrollToBottom(false))
})

onMounted(() => {
  fetchChatList()
})
</script>

<style scoped>
.agent-chat-page {
  min-height: calc(100vh - 5rem);
  background: var(--background);
  margin-bottom: -3rem;
}

.chat-container {
  display: flex;
  height: calc(100vh - 5rem);
  margin: 0 auto;
}

.chat-sidebar {
  width: 320px;
  background: var(--background-light);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.875rem;
  border-bottom: 1px solid var(--border-color);
}

.sidebar-header h2 {
  font-size: 1.25rem;
  color: var(--text-primary);
  margin: 0;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

.chat-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  margin-bottom: 0.8rem;
  background: var(--surface-color);
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.chat-item:hover {
  background: var(--background-median);
  border-color: var(--border-color);
}

.chat-item.active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1), rgba(168, 85, 247, 0.05));
  border-color: var(--primary-color);
}

.chat-avatar {
  width: 2.5rem;
  height: 2.5rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.chat-info {
  flex: 1;
  min-width: 0;
}

.chat-title {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 0.875rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-chat-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.375rem;
  border-radius: 0.25rem;
  opacity: 0;
  transition: all 0.2s;
}

.chat-item:hover .delete-chat-btn {
  opacity: 1;
}

.delete-chat-btn:hover {
  background: var(--error-color);
  color: white;
}

.empty-chat-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.empty-chat-list p {
  color: var(--text-muted);
  margin-bottom: 1.5rem;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: var(--background-light);
  border-bottom: 1px solid var(--border-color);
}

.header-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.agent-icon {
  font-size: 1.5rem;
}

.agent-name {
  font-weight: 600;
  color: var(--text-primary);
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 16.5rem);
  max-height: calc(100vh - 16.5rem);
}

.empty-chat-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
}

.empty-chat-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.empty-chat-state p {
  color: var(--text-secondary);
  margin: 0;
  font-size: 1.125rem;
}

.empty-chat-hint {
  color: var(--text-muted) !important;
  font-size: 0.875rem !important;
  margin-top: 0.5rem !important;
}

.empty-loading-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.message-wrapper {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.message-wrapper.user-message {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  flex-shrink: 0;
  overflow: hidden;
}

.agent-avatar-img {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 50%;
}

.user-message .message-avatar {
  background: linear-gradient(135deg, #10b981, #059669);
}

.assistant-message .message-avatar {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
}

.message-content {
  max-width: 70%;
}

.user-message .message-content {
  text-align: right;
}

.message-text {
  padding: 0.875rem 1.25rem;
  border-radius: 1rem;
  line-height: 1.6;
  word-break: break-word;
}

.tool-message {
  justify-content: flex-start;
}

.tool-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  font-size: 0.8rem;
  max-width: 85%;
  animation: toolFadeIn 0.3s ease-out;
}

.tool-call {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(168, 85, 247, 0.05));
  border: 1px solid rgba(99, 102, 241, 0.2);
  color: var(--text-secondary);
}

.tool-done {
  background: rgba(16, 185, 129, 0.06);
  border: 1px solid rgba(16, 185, 129, 0.15);
  color: var(--text-muted);
}

.tool-spinner {
  width: 0.75rem;
  height: 0.75rem;
  border: 2px solid rgba(99, 102, 241, 0.2);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: toolSpin 0.8s linear infinite;
  flex-shrink: 0;
}

.tool-dot {
  width: 0.75rem;
  height: 0.75rem;
  background: #10b981;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.55rem;
  color: white;
  flex-shrink: 0;
}

.tool-text {
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-text strong {
  color: var(--primary-color);
  font-weight: 600;
}

.tool-done .tool-text strong {
  color: #10b981;
}

@keyframes toolSpin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes toolFadeIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.user-message .message-text {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: white;
  border-top-right-radius: 0.25rem;
}

.assistant-message .message-text {
  background: var(--background-light);
  color: var(--text-primary);
  border-top-left-radius: 0.25rem;
}

.consider-message {
  justify-content: flex-start;
}

.consider-message .message-content {
  max-width: 70%;
}

.consider-message .message-text {
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.08), rgba(245, 158, 11, 0.05));
  border: 1px solid rgba(251, 191, 36, 0.2);
  color: var(--text-secondary);
  border-radius: 1rem;
  border-top-left-radius: 0.25rem;
}

.consider-text {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.consider-icon {
  font-size: 1rem;
  flex-shrink: 0;
}

.consider-label {
  font-weight: 500;
  color: rgba(251, 191, 36, 0.8);
  flex-shrink: 0;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4),
.message-text :deep(h5),
.message-text :deep(h6) {
  margin-top: 1rem;
  margin-bottom: 0.5rem;
  font-weight: 600;
  color: var(--text-primary);
}

.message-text :deep(h1) {
  font-size: 1.5rem;
}

.message-text :deep(h2) {
  font-size: 1.25rem;
}

.message-text :deep(h3) {
  font-size: 1.1rem;
}

.message-text :deep(p) {
  margin-bottom: 0.75rem;
  line-height: 1.6;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 0.5rem 0;
  padding-left: 1.5rem;
}

.message-text :deep(li) {
  margin-bottom: 0.25rem;
  line-height: 1.5;
}

.message-text :deep(code) {
  background: rgba(99, 102, 241, 0.1);
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.875rem;
  color: var(--primary-color);
}

.message-text :deep(pre) {
  background: var(--background-median);
  padding: 1rem;
  border-radius: 0.5rem;
  overflow-x: auto;
  margin: 0.75rem 0;
}

.message-text :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--text-primary);
}

.message-text :deep(blockquote) {
  border-left: 3px solid var(--primary-color);
  padding-left: 1rem;
  margin: 0.75rem 0;
  color: var(--text-secondary);
  font-style: italic;
}

.message-text :deep(a) {
  color: var(--primary-color);
  text-decoration: underline;
}

.message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0.75rem 0;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid var(--border-color);
  padding: 0.5rem;
  text-align: left;
}

.message-text :deep(th) {
  background: var(--background-median);
  font-weight: 600;
}

.message-text :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 1rem 0;
}

.message-text :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.5rem;
  margin: 0.5rem 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  padding: 0.75rem;
}

.typing-dots {
  display: flex;
  gap: 0.375rem;
}

.typing-dots span {
  width: 0.5rem;
  height: 0.5rem;
  background: var(--primary-color);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-container {
  display: flex;
  gap: 0.75rem;
  padding: 1.5rem 1.5rem;
  background: var(--background-light);
  border-top: 1px solid var(--border-color);
}

.message-input {
  flex: 1;
  padding: 0.875rem 1.25rem;
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: 1rem;
  color: var(--text-primary);
  font-size: 0.875rem;
  resize: none;
  transition: all 0.2s;
}

.message-input:focus {
  outline: none;
  border-color: var(--primary-color);
}

.message-input::placeholder {
  color: var(--text-muted);
}

.message-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.875rem 1.5rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border: none;
  border-radius: 1rem;
  color: white;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-icon {
  font-size: 1rem;
}

.welcome-screen {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
}

.welcome-icon {
  font-size: 5rem;
  margin-bottom: 1.5rem;
  width: 9rem;
  height: 9rem;
}

.welcome-screen h1 {
  font-size: 2.5rem;
  color: var(--text-primary);
  margin-bottom: 0.75rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-screen p {
  color: var(--text-secondary);
  font-size: 1.125rem;
  margin-bottom: 2rem;
}

.features {
  display: flex;
  gap: 2rem;
}

.feature {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem;
  background: var(--background-light);
  border-radius: 1rem;
}

.feature-icon {
  font-size: 2rem;
}

.feature span:last-child {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

@media (max-width: 768px) {
  .chat-sidebar {
    width: 100%;
    position: absolute;
    z-index: 100;
    height: calc(100vh - 5rem);
  }
  
  .chat-main {
    width: 100%;
  }
  
  .message-content {
    max-width: 85%;
  }
}
</style>