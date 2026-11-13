<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { request, write } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import type { PageResult, Residency } from '../housing/types'
import { categories, statuses, type Repair, type RepairDetail } from './types'
import RepairImage from './RepairImage.vue'

const router = useRouter(), isStudent = computed(() => currentUser.value?.role === 'STUDENT')
const query = reactive({ q: '', status: '', size: 10 }), result = ref<PageResult<Repair>>({ items: [], total: 0, page: 1, size: 10 })
const loading = ref(false), error = ref(''), success = ref(''), residence = ref<Residency | null>(null), residenceError = ref('')
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))
const formOpen = ref(false), saving = ref(false), formError = ref(''), description = ref(''), category = ref<keyof typeof categories>('PLUMBING')
const files = ref<File[]>([]), previews = ref<string[]>([]), requestId = ref('')
const detailOpen = ref(false), detail = ref<RepairDetail | null>(null), detailLoading = ref(false), detailError = ref(''), detailId = ref(0)
const cancelling = ref(false), reason = ref('')
const authorization = (failure: unknown) => handleAuthorizationFailure(failure, router)
const message = (failure: unknown) => failure instanceof Error ? failure.message : '服务暂时不可用，请重试'
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN')

watch([category, description, files], () => { requestId.value = crypto.randomUUID() })
watch(files, () => { previews.value.forEach(URL.revokeObjectURL); previews.value = files.value.map(file => URL.createObjectURL(file)) })
onUnmounted(() => previews.value.forEach(URL.revokeObjectURL))
async function loadResidence() {
  residence.value = null; residenceError.value = ''
  if (!isStudent.value) return
  try { residence.value = (await request<{ current: Residency | null }>('/api/housing/my-residency')).current }
  catch (failure) { if (!await authorization(failure)) residenceError.value = message(failure) }
}
async function load(page = 1) {
  if (loading.value) return
  loading.value = true; error.value = ''; result.value.items = []
  try {
    const params = new URLSearchParams({ q: query.q, size: String(query.size), page: String(page) })
    if (query.status) params.set('status', query.status)
    let data = await request<PageResult<Repair>>(`/api/repairs?${params}`)
    const last = Math.max(1, Math.ceil(data.total / data.size))
    if (page > last) { params.set('page', String(last)); data = await request<PageResult<Repair>>(`/api/repairs?${params}`) }
    result.value = data
  } catch (failure) { if (!await authorization(failure)) error.value = message(failure) }
  finally { await loadResidence(); loading.value = false }
}
function openCreate() {
  category.value = 'PLUMBING'; description.value = ''; files.value = []; requestId.value = crypto.randomUUID()
  formError.value = ''; success.value = ''; formOpen.value = true
}
function chooseFiles(event: Event) {
  const input = event.target as HTMLInputElement, selected = Array.from(input.files ?? [])
  if (selected.length > 5 || selected.some(file => file.size === 0 || file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/webp'].includes(file.type))) {
    formError.value = '请选择 JPG、PNG 或 WebP 图片，每单最多 5 张，每张不超过 5 MB。'; input.value = ''; return
  }
  files.value = selected; formError.value = ''
}
async function submit() {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try {
    const body = new FormData(); body.set('category', category.value); body.set('description', description.value); body.set('requestId', requestId.value)
    files.value.forEach(file => body.append('images', file))
    await write<RepairDetail>('/api/repairs', body)
    formOpen.value = false; files.value = []; success.value = '报修已提交，等待分配维修人员。'; await load()
  } catch (failure) { if (!await authorization(failure)) { formError.value = message(failure); await loadResidence() } }
  finally { saving.value = false }
}
async function view(id: number, preserveReason = false) {
  if (detailLoading.value || cancelling.value) return
  detailId.value = id; detailOpen.value = true; detail.value = null; detailError.value = ''; detailLoading.value = true
  if (!preserveReason) reason.value = ''
  try { detail.value = await request<RepairDetail>(`/api/repairs/${id}`) }
  catch (failure) { if (!await authorization(failure)) detailError.value = message(failure) }
  finally { detailLoading.value = false }
}
async function cancel() {
  if (cancelling.value || !detail.value) return
  cancelling.value = true; detailError.value = ''
  try {
    detail.value = await write<RepairDetail>(`/api/repairs/${detail.value.id}/cancel`, JSON.stringify({ reason: reason.value }), 'application/json')
    reason.value = ''; success.value = '工单已取消，历史记录继续保留。'; await load(result.value.page)
  } catch (failure) { if (!await authorization(failure)) detailError.value = message(failure) }
  finally { cancelling.value = false }
}
onMounted(() => load())
</script>
<template>
  <section class="page-head account-heading"><div><p class="eyebrow">REPAIRS / 报修服务</p><h1>{{ isStudent ? '我的报修' : '报修管理' }}</h1><p class="subtitle">{{ isStudent ? '寝室有问题，随时告诉我们。提交需求，查看进度。' : '查看维修需求，留存每次处理记录。' }}</p></div><ElButton v-if="isStudent" :disabled="loading || !residence || !!residenceError" @click="openCreate">新建报修</ElButton></section>
  <p v-if="isStudent && !loading" class="quiet-note" data-testid="repair-residency"><template v-if="residenceError">住宿读取失败：{{ residenceError }} <button class="text-button" @click="load(result.page)">刷新住宿</button></template><template v-else-if="residence">当前住宿：{{ residence.buildingName }} · {{ residence.roomNo }} · {{ residence.bedNo }} 号床</template><template v-else>尚未入住，暂不能新建报修。本人历史工单仍可查看。</template></p>
  <section class="card account-card">
    <form class="account-filters" @submit.prevent="load()"><div class="field search-field"><label for="repair-search">搜索报修</label><ElInput id="repair-search" v-model="query.q" placeholder="问题描述、楼栋、寝室或提交人" maxlength="64" :disabled="loading" /></div><div class="field"><label for="repair-status">工单状态</label><select id="repair-status" v-model="query.status" :disabled="loading"><option value="">全部状态</option><option v-for="(label, value) in statuses" :key="value" :value="value">{{ label }}</option></select></div><ElButton native-type="submit" :disabled="loading" :loading="loading">查询</ElButton></form>
    <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load(result.page)">重新加载</button></p><p v-if="success" class="message success" role="status">{{ success }}</p>
    <div class="account-table-wrap" :aria-busy="loading"><table class="account-table repair-table"><caption class="sr-only">报修工单</caption><thead><tr><th>工单与问题</th><th>提交位置</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.id"><td><b>#{{ item.id }} · {{ categories[item.category] }}</b><span class="repair-summary">{{ item.description }}</span></td><td>{{ item.buildingName }}<span class="account-name">{{ item.roomNo }} · {{ item.bedNo }} 号床</span><span v-if="!isStudent" class="account-name">{{ item.studentName }}</span></td><td><span class="account-status" :class="{ inactive: item.status === 'CANCELLED' }">{{ statuses[item.status] }}</span></td><td>{{ formatTime(item.createdAt) }}</td><td><div class="account-actions"><button :disabled="loading" @click="view(item.id)">查看详情</button></div></td></tr></tbody></table><p v-if="loading" class="account-empty" role="status">正在读取工单…</p><p v-else-if="!result.items.length && !error" class="account-empty">暂无符合条件的报修工单。</p></div>
    <div class="account-pagination"><span>共 {{ result.total }} 条记录</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" :disabled="loading" @change="load()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="loading || result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span>{{ result.page }} / {{ pages }}</span><ElButton :disabled="loading || result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
  </section>
  <ElDialog v-model="formOpen" title="新建报修" width="680px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close><form class="form-stack" @submit.prevent="submit"><p class="form-hint">{{ residence ? `${residence.buildingName} · ${residence.roomNo}` : '请先办理入住' }} · 提交时按当前住宿登记位置。</p><div class="field"><label for="repair-category">问题类型</label><select id="repair-category" v-model="category" :disabled="saving"><option v-for="(label, value) in categories" :key="value" :value="value">{{ label }}</option></select></div><div class="field"><label for="repair-description">问题描述</label><ElInput id="repair-description" v-model="description" type="textarea" :rows="4" required maxlength="2000" show-word-limit :disabled="saving" placeholder="请说明问题位置、现象及影响，最多 2000 字" /></div><div class="field"><label for="repair-files">现场图片</label><input id="repair-files" type="file" accept="image/jpeg,image/png,image/webp" multiple :disabled="saving" @change="chooseFiles" /><p class="form-hint">可选 JPG / PNG / WebP，最多 5 张，每张不超过 5 MB、1600 万像素。</p></div><div v-if="files.length" class="repair-previews"><figure v-for="(file, index) in files" :key="index"><img :src="previews[index]" :alt="`待提交图片 ${index + 1}`" /><figcaption>{{ file.name }}</figcaption><button type="button" class="text-button" :disabled="saving" @click="files = files.filter((_, i) => i !== index)">移除图片 {{ index + 1 }}</button></figure></div><p v-if="formError" class="message error" role="alert">{{ formError }}</p><div class="form-actions"><ElButton native-type="submit" :loading="saving" :disabled="saving">提交报修</ElButton><ElButton :disabled="saving" @click="formOpen = false">返回</ElButton></div></form></ElDialog>
  <ElDialog v-model="detailOpen" :title="`工单详情 #${detailId}`" width="760px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!cancelling" :show-close="!cancelling" destroy-on-close><p v-if="detailLoading" role="status">正在读取详情…</p><p v-if="detailError" class="message error" role="alert">{{ detailError }} <button class="text-button" :disabled="detailLoading || cancelling" @click="view(detailId, true)">刷新详情</button></p><template v-if="detail"><div class="repair-detail-head"><h2>{{ categories[detail.category] }}</h2><span class="account-status" :class="{ inactive: detail.status === 'CANCELLED' }">{{ statuses[detail.status] }}</span></div><p class="form-hint">提交位置：{{ detail.buildingName }} · {{ detail.roomNo }} · {{ detail.bedNo }} 号床<br />{{ detail.studentName }} · {{ formatTime(detail.createdAt) }}</p><p class="repair-description">{{ detail.description }}</p><div class="repair-images"><RepairImage v-for="(item, index) in detail.images" :key="item.id" :url="item.url" :index="index" /></div><h3>状态记录</h3><ol class="repair-timeline"><li v-for="log in detail.logs" :key="log.id"><b>{{ statuses[log.toStatus] }}</b><span>{{ formatTime(log.createdAt) }} · {{ log.actorName }}</span><p>{{ log.note }}</p></li></ol><form v-if="detail.status === 'PENDING_ASSIGNMENT'" class="form-stack repair-cancel" @submit.prevent="cancel"><div class="field"><label for="repair-reason">取消原因</label><ElInput id="repair-reason" v-model="reason" type="textarea" :rows="2" required maxlength="500" :disabled="cancelling" placeholder="如需取消，请说明原因；历史记录将保留" /></div><ElButton native-type="submit" :disabled="cancelling || detailLoading" :loading="cancelling">确认取消工单</ElButton></form></template><div class="form-actions"><ElButton :disabled="cancelling" @click="detailOpen = false">关闭详情</ElButton></div></ElDialog>
</template>
