<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import { request } from '../../lib/http'
import { currentUser, handleAuthorizationFailure, workspacePath, type Profile } from '../identity/session'
import { statuses, categories } from '../repair/types'
import { batchStatuses } from '../hygiene/batch-types'
import type { Dashboard } from './types'

const router = useRouter(), route = useRoute()
const data = ref<Dashboard | null>(null), error = ref(''), loading = ref(true)
const batch = computed(() => data.value?.inspection?.batch)
const number = (value: number) => value.toFixed(2)
let requestId = 0
async function load() {
  const id = ++requestId; loading.value = true; error.value = ''; data.value = null
  try {
    // Read locally first: an old page must not overwrite a newer session.
    const user = await request<Profile>('/api/auth/me')
    if (id !== requestId) return
    currentUser.value = user
    if (route.params.role !== user.role) { await router.replace(workspacePath()); return }
    const next = await request<Dashboard>('/api/dashboard')
    if (id !== requestId) return
    data.value = next
  } catch (failure) {
    if (id === requestId && !await handleAuthorizationFailure(failure, router)) error.value = failure instanceof Error ? failure.message : '工作台读取失败，请重试'
  } finally { if (id === requestId) loading.value = false }
}
function refresh() { void load() }
watch(() => route.params.role, refresh)
onMounted(() => { refresh(); window.addEventListener('focus', refresh) })
onUnmounted(() => { requestId++; window.removeEventListener('focus', refresh) })
</script>
<template>
  <section class="page-head account-heading"><div><p class="eyebrow">MY WORKSPACE / 个人工作台</p><h1>{{ currentUser?.roleLabel }}工作台</h1><p class="subtitle">{{ currentUser?.displayName }}，欢迎回来。{{ data?.role === 'STUDENT' ? '住宿、成绩与报修，在这里查看。' : '从今天的待办开始，及时跟进校园生活。' }}</p></div><ElButton :disabled="loading" :loading="loading" @click="load">刷新工作台</ElButton></section>
  <p v-if="loading" role="status" class="card dashboard-loading">正在读取工作台…</p>
  <section v-else-if="error" class="card dashboard-loading"><p class="message error" role="alert">{{ error }}</p><ElButton @click="load">重新加载</ElButton></section>
  <div v-else-if="data && data.role === currentUser?.role" data-testid="dashboard-content" class="dashboard-content">
    <div v-if="data.inspection" class="dashboard-overview">
      <section class="card dashboard-card" data-testid="dashboard-inspection">
        <div class="dashboard-card-head"><h2>本周检查进度</h2><span class="role-badge">{{ data.inspection.isoYear }} · 第{{ data.inspection.isoWeek }}周</span></div>
        <template v-if="batch"><p class="dashboard-value">{{ batch.inspectedCount }} <small>/ {{ batch.expectedCount }} 间</small></p><p class="subtitle">已检 / 应检 · {{ batchStatuses[batch.status] }}</p>
          <template v-if="batch.completionRate !== null"><progress max="100" :value="batch.completionRate" aria-label="本周检查完成率" /><p class="form-hint">完成率 {{ number(batch.completionRate) }}%</p></template><p v-else class="subtitle">无应检寝室</p>
          <div class="dashboard-links"><RouterLink :to="{ path: '/inspections', query: { batch: batch.id } }">查看检查名单</RouterLink><RouterLink v-if="batch.expectedCount > 0" :to="{ path: '/inspections', query: { batch: batch.id, inspected: 'false' } }">未检查 {{ batch.expectedCount - batch.inspectedCount }} 间</RouterLink></div>
          <p v-if="!batch.scoringAllowed" class="form-hint">当前批次不可录分，可进入查看记录。</p>
        </template><template v-else><p class="dashboard-empty">本周检查尚未准备</p><p class="form-hint">进入检查页面准备名单后，可查看进度。</p><RouterLink class="dashboard-link" to="/inspections">进入本周检查</RouterLink></template>
      </section>
      <section v-if="data.rectifications" class="card dashboard-card" data-testid="dashboard-rectifications"><div class="dashboard-card-head"><h2>整改待办</h2><RouterLink class="dashboard-link" to="/rectifications">查看全部</RouterLink></div><p class="dashboard-value">{{ data.rectifications.total }} <small>项未关闭</small></p>
        <ul v-if="data.rectifications.items.length" class="dashboard-list"><li v-for="event in data.rectifications.items" :key="event.id"><RouterLink :to="{ path: '/rectifications', query: { record: event.id } }"><b>{{ event.buildingName }} · {{ event.roomNo }}</b><span>{{ event.verifiedAt ? '已核实，待关闭' : '待整改 / 待核实' }}</span></RouterLink></li></ul><p v-else class="dashboard-empty">暂无整改待办</p><p v-if="data.rectifications.total > 5" class="form-hint">展示最近创建的5项，更多待办请查看全部。</p>
      </section>
    </div>
    <div v-if="data.role === 'STUDENT'" class="dashboard-overview">
      <section class="card dashboard-card" data-testid="dashboard-residency"><h2>当前住宿</h2><template v-if="data.residency"><p class="dashboard-value dashboard-location">{{ data.residency.buildingName }}</p><p class="subtitle">{{ data.residency.roomNo }} · {{ data.residency.bedNo }} 号床</p></template><p v-else class="dashboard-empty">尚未入住，请联系系统管理员办理入住。</p><RouterLink class="dashboard-link" to="/my-residency">查看我的住宿</RouterLink></section>
      <section class="card dashboard-card" data-testid="dashboard-results"><h2>本周寝室成绩</h2><template v-if="data.results"><p class="form-hint">{{ data.results.period.label }}</p><template v-if="data.results.summary.score !== null"><p class="dashboard-value">{{ number(data.results.summary.score) }} <small>分</small></p><p class="subtitle">当前楼栋第 {{ data.results.summary.rank }} 名</p></template><p v-else class="dashboard-empty">本周暂无有效成绩，未检查不记零分。</p></template><p v-else class="dashboard-empty">入住后可查看寝室成绩。</p><RouterLink class="dashboard-link" to="/my-results">查看成绩与排行</RouterLink></section>
    </div>
    <section aria-label="报修待办" class="dashboard-queues" :class="{ 'dashboard-three': data.repairs.length === 3 }">
      <article v-for="queue in data.repairs" :key="queue.status || 'ALL'" class="card dashboard-card" :data-testid="`queue-${queue.status || 'ALL'}`">
        <div class="dashboard-card-head"><h2>{{ queue.status ? `${statuses[queue.status]}工单` : '我的报修工单' }}</h2><RouterLink class="dashboard-link" :to="{ path: '/repairs', query: queue.status ? { status: queue.status } : {} }">查看全部</RouterLink></div>
        <p class="dashboard-value">{{ queue.page.total }} <small>条</small></p>
        <ul v-if="queue.page.items.length" class="dashboard-list"><li v-for="order in queue.page.items" :key="order.id"><RouterLink :to="{ path: '/repairs', query: { record: order.id, ...(queue.status ? { status: queue.status } : {}) } }"><b>#{{ order.id }} · {{ categories[order.category] }}</b><span class="dashboard-description">{{ order.description }}</span><span>{{ order.buildingName }} · {{ order.roomNo }} · {{ statuses[order.status] }}</span></RouterLink></li></ul>
        <p v-else class="dashboard-empty">{{ queue.status ? `暂无${statuses[queue.status]}工单` : '暂无本人报修工单' }}</p><p v-if="queue.page.total > 5" class="form-hint">展示最近提交的5条，更多工单请查看全部。</p>
      </article>
    </section>
    <nav class="card dashboard-shortcuts" aria-label="常用业务入口"><h2>常用入口</h2>
      <template v-if="data.inspection"><RouterLink to="/statistics">查看卫生统计</RouterLink><RouterLink to="/schemes">查看评分方案</RouterLink><RouterLink to="/housing">查看住宿资料</RouterLink></template>
      <template v-if="data.role === 'SYSTEM_ADMIN'"><RouterLink to="/accounts">管理账号</RouterLink><RouterLink to="/residencies">办理入住与调寝</RouterLink></template>
      <RouterLink to="/repairs">{{ data.role === 'STUDENT' ? '发起报修与查看进度' : data.role === 'MAINTAINER' ? '查看全部维修任务' : '查看全部报修' }}</RouterLink>
      <RouterLink to="/profile">查看个人资料</RouterLink><RouterLink to="/password">修改账号密码</RouterLink>
    </nav>
  </div>
</template>
<style scoped>
.dashboard-content { display: grid; gap: 24px; }
.dashboard-overview, .dashboard-queues { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }
.dashboard-queues:has(> :only-child) { grid-template-columns: 1fr; }
.dashboard-three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.dashboard-card, .dashboard-loading { padding: 24px; min-width: 0; }
.dashboard-card h2, .dashboard-shortcuts h2 { font-size: 17px; margin: 0; }
.dashboard-card-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.dashboard-value { color: var(--primary); font-size: 38px; font-weight: 600; margin: 20px 0 8px; line-height: 1.3; }
.dashboard-value small { font-size: 14px; color: var(--muted); font-weight: 400; }
.dashboard-location { font-size: 28px; overflow-wrap: anywhere; }
.dashboard-card progress { width: 100%; margin-top: 18px; accent-color: var(--primary); }
.dashboard-links { display: flex; gap: 20px; flex-wrap: wrap; margin-top: 20px; }
.dashboard-link, .dashboard-links a, .dashboard-shortcuts a { color: var(--primary); font-size: 14px; display: inline-block; padding: 8px 0; }
.dashboard-list { list-style: none; padding: 0; margin: 16px 0 0; }
.dashboard-list li + li { border-top: 1px solid rgba(100, 115, 145, .15); }
.dashboard-list a { display: grid; gap: 6px; padding: 13px 0; border-radius: 6px; overflow-wrap: anywhere; }
.dashboard-list a:hover, .dashboard-shortcuts a:hover { color: var(--primary); }
.dashboard-list b { font-size: 14px; font-weight: 500; }
.dashboard-list span { font-size: 12px; color: var(--muted); }
.dashboard-description { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.dashboard-empty { color: var(--muted); font-size: 14px; line-height: 1.8; margin: 20px 0; }
.dashboard-shortcuts { padding: 20px 24px; display: flex; gap: 16px 24px; align-items: center; flex-wrap: wrap; }
.dashboard-content a:focus-visible { outline: 2px solid var(--primary); outline-offset: 4px; }
@media (max-width: 1200px) { .dashboard-three { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
