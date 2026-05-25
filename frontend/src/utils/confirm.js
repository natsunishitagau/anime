let confirmCallback = null

export const setConfirmHandler = (handler) => {
  confirmCallback = handler
}

export const $confirm = (message) => {
  if (confirmCallback) {
    return confirmCallback(message)
  }
  return Promise.resolve(window.confirm(message))
}

export default $confirm
