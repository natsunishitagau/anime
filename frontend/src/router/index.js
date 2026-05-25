import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/anime/:id',
    name: 'AnimeDetail',
    component: () => import('../views/AnimeDetail.vue')
  },
  {
    path: '/watch/:id',
    name: 'VideoPlayer',
    component: () => import('../views/VideoPlayer.vue')
  },
  {
    path: '/browse',
    name: 'Browse',
    component: () => import('../views/Browse.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('../views/Favorites.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('../views/SearchResult.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const publicPaths = ['/', '/login', '/register', '/search']

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const isAuthenticated = !!token

  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (!isAuthenticated && !publicPaths.includes(to.path)) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  next()
})

export default router
