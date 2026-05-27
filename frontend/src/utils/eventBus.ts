import type { EventBusCallback } from '../types'

class EventBus {
  private listeners: Map<string, Set<EventBusCallback>> = new Map()

  on(event: string, callback: EventBusCallback): void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set())
    }
    this.listeners.get(event)!.add(callback)
  }

  off(event: string, callback: EventBusCallback): void {
    const eventListeners = this.listeners.get(event)
    if (eventListeners) {
      eventListeners.delete(callback)
    }
  }

  emit(event: string, ...args: unknown[]): void {
    const eventListeners = this.listeners.get(event)
    if (eventListeners) {
      eventListeners.forEach(callback => {
        try {
          callback(...args)
        } catch (error) {
          console.error('Event callback error:', error)
        }
      })
    }
  }
}

export const eventBus = new EventBus()
export const MESSAGE_EVENTS = {
  UNREAD_COUNT_CHANGED: 'message:unread-count-changed',
  MESSAGE_READ: 'message:read',
  ALL_MESSAGES_READ: 'message:all-read'
}