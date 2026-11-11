<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppIcon from '../../components/AppIcon.vue'
import { request } from '../../lib/http'
import { currentUser } from './session'

const title = ref(''), error = ref(''), loading = ref(true)
async function load() {
  loading.value = true; error.value = ''
  try { title.value = (await request<{ title: string }>(`/api/identity/workspaces/${currentUser.value!.role}`)).title }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '加载失败' }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <section class="page-head"><p class="eyebrow">MY WORKSPACE / 个人工作台</p><h1>{{ title || '个人工作台' }}</h1><p class="subtitle">{{ currentUser?.displayName }}，欢迎回来。</p></section>
  <p v-if="loading" role="status" class="card">正在读取工作台…</p>
  <div v-else-if="error" class="card"><p class="message error" role="alert">{{ error }}</p><button class="text-button" @click="load">重新加载</button></div>
  <template v-else>
    <section class="card welcome-card"><div><p class="eyebrow">YOUR CAMPUS SPACE</p><h2>让每一天，从容开始。</h2><p class="subtitle">查看个人信息，管理账号安全。<br />你的校园生活，从这里连接。</p></div><span class="welcome-emblem"><AppIcon name="building" /></span></section>
    <section class="quick-grid" aria-label="账号与个人信息">
      <RouterLink class="card quick-card" to="/profile"><span class="feature-icon"><AppIcon name="user" /></span><div><h2>个人资料</h2><p>查看你的账号信息与身份。</p></div><AppIcon name="arrow" /></RouterLink>
      <RouterLink class="card quick-card" to="/password"><span class="feature-icon mint"><AppIcon name="key" /></span><div><h2>账号安全</h2><p>更新密码，守护个人空间。</p></div><AppIcon name="arrow" /></RouterLink>
    </section>
    <p class="quiet-note"><AppIcon name="shield" />当前以{{ currentUser?.roleLabel }}身份登录。账号问题请联系系统管理员。</p>
  </template>
</template>
