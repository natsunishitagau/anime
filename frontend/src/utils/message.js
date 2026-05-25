import { createApp, ref } from 'vue'
import Message from '../components/Message.vue'

let app = null
let messageComponent = null
let messageRef = null

const createMessageContainer = () => {
  if (app) return

  const container = document.createElement('div')
  document.body.appendChild(container)

  app = createApp(Message)
  messageComponent = app.mount(container)

  const messageEls = container.querySelectorAll('[data-v-app]')
  if (messageEls.length > 0) {
    messageRef = messageComponent
  }
}

const getMessageRef = () => {
  if (!app) {
    createMessageContainer()
  }
  return messageComponent
}

export const $message = {
  success: (content, duration = 3000) => {
    return getMessageRef()?.success(content, duration)
  },
  error: (content, duration = 3000) => {
    return getMessageRef()?.error(content, duration)
  },
  warning: (content, duration = 3000) => {
    return getMessageRef()?.warning(content, duration)
  },
  info: (content, duration = 3000) => {
    return getMessageRef()?.info(content, duration)
  },
  add: (content, type, duration) => {
    return getMessageRef()?.addMessage(content, type, duration)
  },
  remove: (id) => {
    return getMessageRef()?.removeMessage(id)
  }
}

export default $message
