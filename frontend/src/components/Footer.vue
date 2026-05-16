<template>
  <footer class="footer">
    <div class="footer-container">
      <div class="footer-content">
        <div class="footer-section">
          <h3>关于我们</h3>
          <p>AnimeHub 是一个专业的动漫推荐平台，为您提供最新的番剧资讯和个性化推荐服务。</p>
        </div>
        
        <div class="footer-section">
          <h3>快速链接</h3>
          <ul>
            <li><router-link to="/">首页</router-link></li>
            <li><router-link to="/browse">浏览番剧</router-link></li>
            <li><router-link to="/browse?type=TV">TV动画</router-link></li>
            <li><router-link to="/browse?type=MOVIE">剧场版</router-link></li>
          </ul>
        </div>
        
        <div class="footer-section">
          <h3>热门分类</h3>
          <ul>
            <li><router-link to="/browse?genre=动作">动作</router-link></li>
            <li><router-link to="/browse?genre=剧情">剧情</router-link></li>
            <li><router-link to="/browse?genre=喜剧">喜剧</router-link></li>
            <li><router-link to="/browse?genre=奇幻">奇幻</router-link></li>
          </ul>
        </div>
        
        <div class="footer-section">
          <h3>免责声明</h3>
          <p class="disclaimer">本站所有内容均来自互联网，仅供学习交流之用，如有侵权请联系删除。</p>
        </div>
      </div>
      
      <div class="footer-bottom">
        <p>&copy; 2024 AnimeHub. All rights reserved.</p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  background: var(--background-light);
  border-top: 1px solid var(--border-color);
  margin-top: auto;
}

.footer-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 3rem 1.5rem 1.5rem;
}

.footer-content {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 2rem;
  margin-bottom: 2rem;
}

.footer-section h3 {
  color: var(--text-primary);
  font-size: 1rem;
  margin-bottom: 1rem;
}

.footer-section p {
  color: var(--text-secondary);
  font-size: 0.875rem;
  line-height: 1.6;
}

.footer-section ul {
  list-style: none;
}

.footer-section li {
  margin-bottom: 0.5rem;
}

.footer-section a {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.875rem;
  transition: color 0.2s;
}

.footer-section a:hover {
  color: var(--primary-color);
}

.disclaimer {
  font-size: 0.75rem !important;
  color: var(--text-muted) !important;
}

.footer-bottom {
  padding-top: 1.5rem;
  border-top: 1px solid var(--border-color);
  text-align: center;
}

.footer-bottom p {
  color: var(--text-muted);
  font-size: 0.875rem;
}
</style>