<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { currentUser, logout, workspacePath } from '../features/identity/session'

const busy = ref(false), error = ref(''), router = useRouter()
async function signOut() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await logout(); await router.replace('/login') }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '退出失败，请重试' }
  finally { busy.value = false }
}
</script>
<template>
  <div v-if="currentUser" class="app-shell">
    <aside class="sidebar">
      <RouterLink :to="workspacePath()" class="brand" aria-label="栖居首页"><span class="brand-symbol"><AppIcon name="building" /></span><span><b>栖居</b><small>高校宿舍管理系统</small></span></RouterLink>
      <p class="nav-label">个人空间</p>
      <nav aria-label="主要导航">
        <RouterLink v-if="currentUser.role === 'SYSTEM_ADMIN'" to="/accounts" class="nav-item"><AppIcon name="user" />账号管理</RouterLink>
        <RouterLink :to="workspacePath()" class="nav-item"><AppIcon name="home" />个人工作台</RouterLink>
        <RouterLink to="/profile" class="nav-item"><AppIcon name="user" />个人资料</RouterLink>
        <RouterLink to="/password" class="nav-item"><AppIcon name="key" />修改密码</RouterLink>
      </nav>
      <div class="sidebar-bottom"><div class="security-note"><AppIcon name="shield" /><p>安心，从账号安全开始</p><small>妥善保管账号，离开前记得退出。</small></div><p class="side-footer">栖居 · 校园生活</p></div>
    </aside>
    <div class="app-content">
      <header class="topbar"><p class="breadcrumb">个人空间 <span>/</span> <b>{{ currentUser.roleLabel }}</b></p><div class="top-actions"><span class="role-badge">{{ currentUser.roleLabel }}</span><span class="avatar" aria-hidden="true">{{ currentUser.displayName.slice(-1) }}</span><span>{{ currentUser.displayName }}</span><ElButton :loading="busy" :disabled="busy" @click="signOut">退出登录</ElButton></div></header>
      <main class="main"><p v-if="error" class="message error" role="alert">{{ error }}</p><RouterView /></main>
    </div>
  </div>
</template>
