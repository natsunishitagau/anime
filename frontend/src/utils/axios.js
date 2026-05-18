import axios from 'axios'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    console.log('📡 请求信息:', {
      fullUrl: `${config.baseURL}${config.url}`,
      params: config.params,
      data: config.data,
      headers: config.headers
    })
    return config
  },
  (error) => {
    console.error('❌ 请求错误:', error)
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response) => {
    console.log('✅ 响应信息:', {
      status: response.status,
      statusText: response.statusText,
      data: response.data,
    })
    return response
  },
  (error) => {
    console.error('❌ 响应错误:', {
      status: error.response?.status,
      message: error.response?.data?.message || error.message,
      code: error.code,
      fullUrl: error.config?.baseURL + error.config?.url
    })
    return Promise.reject(error)
  }
)

export default instance