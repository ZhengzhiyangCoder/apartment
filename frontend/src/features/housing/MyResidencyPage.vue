<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElButton } from 'element-plus'
import { RouterLink } from 'vue-router'
import { request } from '../../lib/http'
import type { Residency } from './types'

const current = ref<Residency | null>(null), loading = ref(false), error = ref('')
async function load() {
  if (loading.value) return
  loading.value = true; error.value = ''; current.value = null
  try { current.value = (await request<{ current: Residency | null }>('/api/housing/my-residency')).current }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '读取失败，请重试' }
  finally { loading.value = false }
}
onMounted(() => { load(); window.addEventListener('focus', load) })
onUnmounted(() => window.removeEventListener('focus', load))
</script>
<template>
  <section class="page-head account-heading"><div><p class="eyebrow">MY RESIDENCE / 我的住宿</p><h1>我的住宿</h1><p class="subtitle">校园里的安心一隅，你的当前住宿信息。</p></div><ElButton :loading="loading" :disabled="loading" @click="load">刷新住宿</ElButton></section>
  <section v-if="loading" class="card" role="status">正在读取当前住宿…</section>
  <section v-else-if="error" class="card"><p class="message error" role="alert">{{ error }}</p><ElButton @click="load">重新加载</ElButton></section>
  <section v-else-if="current" class="card" data-testid="current-residency"><p class="eyebrow">当前住宿</p><h2>{{ current.buildingName }} · {{ current.roomNo }}</h2><p class="subtitle">{{ current.bedNo }} 号床</p><p class="form-hint">入住时间：{{ new Date(current.startedAt).toLocaleString('zh-CN') }}</p><RouterLink to="/my-results" class="text-button">查看寝室成绩</RouterLink><p class="quiet-note">如需调寝或退宿，请联系系统管理员。</p></section>
  <section v-else class="card"><h2>尚未入住</h2><p class="subtitle">请联系系统管理员办理入住。入住后可使用寝室报修、查看当前寝室成绩和楼栋排行。</p><p class="form-hint">未入住不影响个人资料与本人历史报修的查看权限。</p></section>
</template>
