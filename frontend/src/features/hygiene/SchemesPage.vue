<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { request, write } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import { schemeStatuses, type Scheme, type SchemeDetail, type SchemePage } from './types'

const router = useRouter(), isAdmin = computed(() => currentUser.value?.role === 'SYSTEM_ADMIN')
const query = reactive({ q: '', status: '', size: 10 })
const result = ref<SchemePage>({ items: [], total: 0, page: 1, size: 10, active: null })
const loading = ref(false), error = ref(''), success = ref(''), copying = ref(false)
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))
const editorOpen = ref(false), editorId = ref<number | null>(null), editorVersion = ref(0), saving = ref(false), formError = ref('')
const form = reactive({ name: '', threshold: '60', dimensions: [{ name: '', weight: '' }] })
const detailOpen = ref(false), detailId = ref(0), detail = ref<SchemeDetail | null>(null), detailLoading = ref(false), detailError = ref('')
const activationOpen = ref(false), activationId = ref(0), activation = ref<SchemeDetail | null>(null), previousActive = ref<Scheme | null>(null)
const activationLoading = ref(false), activating = ref(false), activationError = ref('')
const authorize = (failure: unknown) => handleAuthorizationFailure(failure, router)
const message = (failure: unknown) => failure instanceof Error ? failure.message : '服务暂时不可用，请重试'
const time = (value: string) => new Date(value).toLocaleString('zh-CN')
const decimal = (value: number) => Number(value).toFixed(2)
function hundredths(value: string) {
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  return cents <= 10000 ? cents : null
}
const total = computed(() => {
  const weights = form.dimensions.map(d => hundredths(d.weight))
  return weights.some(w => w === null) ? null : weights.reduce<number>((sum, w) => sum + w!, 0) / 100
})
async function load(page = 1) {
  if (loading.value) return
  loading.value = true; error.value = ''; result.value.items = []; result.value.active = null
  try {
    const params = new URLSearchParams({ q: query.q, page: String(page), size: String(query.size) })
    if (query.status) params.set('status', query.status)
    let data = await request<SchemePage>(`/api/hygiene/schemes?${params}`)
    const last = Math.max(1, Math.ceil(data.total / data.size))
    if (page > last) { params.set('page', String(last)); data = await request<SchemePage>(`/api/hygiene/schemes?${params}`) }
    result.value = data
  } catch (failure) { if (!await authorize(failure)) error.value = message(failure) }
  finally { loading.value = false }
}
function openEditor(data?: SchemeDetail) {
  editorId.value = data?.id ?? null; editorVersion.value = data?.version ?? 0
  form.name = data?.name ?? ''; form.threshold = String(data?.threshold ?? 60)
  form.dimensions = data?.dimensions.map(d => ({ name: d.name, weight: String(d.weight) })) ?? [{ name: '', weight: '' }]
  formError.value = ''; detailOpen.value = false; editorOpen.value = true
}
async function edit(id: number) {
  if (saving.value) return
  saving.value = true
  try {
    const latest = await request<SchemeDetail>(`/api/hygiene/schemes/${id}`)
    if (latest.status !== 'DRAFT') throw new Error('此版本已启用并固定，请返回列表后复制为新草稿。当前输入仍保留。')
    openEditor(latest)
  }
  catch (failure) { if (!await authorize(failure)) { formError.value = message(failure); error.value = message(failure) } }
  finally { saving.value = false }
}
async function save() {
  if (saving.value) return
  formError.value = ''
  if (total.value === null || hundredths(form.threshold) === null) { formError.value = '权重与整改阈值必须在0–100之间，最多两位小数。'; return }
  saving.value = true
  try {
    const definition = { name: form.name, threshold: Number(form.threshold), dimensions: form.dimensions.map(d => ({ name: d.name, weight: Number(d.weight) })) }
    await write<SchemeDetail>(editorId.value ? `/api/hygiene/schemes/${editorId.value}` : '/api/hygiene/schemes',
      JSON.stringify(editorId.value ? { definition, version: editorVersion.value } : definition), 'application/json')
    editorOpen.value = false; success.value = '评分草稿已保存，启用前请核对权重与整改阈值。'; await load()
  } catch (failure) { if (!await authorize(failure)) formError.value = message(failure) }
  finally { saving.value = false }
}
async function copy(item: Scheme) {
  if (copying.value) return
  copying.value = true; error.value = ''; detailError.value = ''
  try {
    const draft = await write<SchemeDetail>(`/api/hygiene/schemes/${item.id}/copy`, JSON.stringify({ version: item.version }), 'application/json')
    success.value = '新草稿已建立，调整后保存并启用；原版本保留。'; await load(); openEditor(draft)
  } catch (failure) { if (!await authorize(failure)) { error.value = message(failure); detailError.value = message(failure) } }
  finally { copying.value = false }
}
async function view(id: number) {
  if (detailLoading.value) return
  detailId.value = id; detailOpen.value = true; detail.value = null; detailError.value = ''; detailLoading.value = true
  try { detail.value = await request<SchemeDetail>(`/api/hygiene/schemes/${id}`) }
  catch (failure) { if (!await authorize(failure)) detailError.value = message(failure) }
  finally { detailLoading.value = false }
}
async function prepareActivation(id: number) {
  if (activating.value || activationLoading.value) return
  activationId.value = id; activationOpen.value = true; activationLoading.value = true; activationError.value = ''; activation.value = null
  try {
    const [target, current] = await Promise.all([request<SchemeDetail>(`/api/hygiene/schemes/${id}`), request<SchemePage>('/api/hygiene/schemes?size=1')])
    activation.value = target; previousActive.value = current.active
    if (target.status !== 'DRAFT') activationError.value = '此版本已启用或归档，请返回列表查看当前标准；调整请复制为新草稿。'
  } catch (failure) { if (!await authorize(failure)) activationError.value = message(failure) }
  finally { activationLoading.value = false }
}
async function activate() {
  if (activating.value || activationLoading.value || !activation.value) return
  activating.value = true; activationError.value = ''
  try {
    await write<SchemeDetail>(`/api/hygiene/schemes/${activation.value.id}/activate`, JSON.stringify({ version: activation.value.version, activeId: previousActive.value?.id ?? 0 }), 'application/json')
    activationOpen.value = false; success.value = '方案已启用，后续新检查批次将采用此版本。'; await load(result.value.page)
  } catch (failure) { if (!await authorize(failure)) activationError.value = message(failure) }
  finally { activating.value = false }
}
const route = useRoute()
onMounted(async () => {
  await load()
  const id = Number(route.query.record)
  if (Number.isSafeInteger(id) && id > 0) await view(id)
})
</script>

<template>
  <section class="page-head account-heading"><div><p class="eyebrow">HYGIENE / 卫生标准</p><h1>评分方案</h1><p class="subtitle">{{ isAdmin ? '维护评分维度与权重，为每次检查保留明确的标准。' : '查看当前评分标准，追溯历史版本。' }}</p></div><ElButton v-if="isAdmin" :disabled="loading || saving" @click="openEditor()">新建评分草稿</ElButton></section>
  <section class="card scheme-current" data-testid="active-scheme">
    <p v-if="loading" role="status">正在读取当前标准…</p><p v-else-if="error">当前启用方案读取失败，请重新加载。</p>
    <template v-else-if="result.active"><div><p class="eyebrow">当前启用</p><h2>#{{ result.active.id }} · {{ result.active.name }}</h2><p class="subtitle">{{ result.active.dimensionCount }}个维度 · 整改阈值 {{ decimal(result.active.threshold) }} 分</p></div><ElButton @click="view(result.active.id)">查看当前标准</ElButton></template>
    <p v-else>暂无启用方案，待系统管理员启用后用于新检查批次。</p>
  </section>
  <section class="card account-card">
    <form class="account-filters" @submit.prevent="load()"><div class="field search-field"><label for="scheme-search">搜索方案</label><ElInput id="scheme-search" v-model="query.q" maxlength="64" placeholder="按方案名称搜索" :disabled="loading" /></div><div class="field"><label for="scheme-status">方案状态</label><select id="scheme-status" v-model="query.status" :disabled="loading"><option value="">全部状态</option><option v-if="isAdmin" value="DRAFT">草稿</option><option value="ACTIVE">已启用</option><option value="ARCHIVED">历史版本</option></select></div><ElButton native-type="submit" :loading="loading" :disabled="loading">查询</ElButton></form>
    <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load(result.page)">重新加载</button></p><p v-if="success" class="message success" role="status">{{ success }}</p>
    <div class="account-table-wrap" :aria-busy="loading"><table class="account-table scheme-table"><caption class="sr-only">评分方案版本</caption><thead><tr><th>版本与名称</th><th>状态</th><th>维度与权重</th><th>整改阈值</th><th>操作</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.id"><td><b>#{{ item.id }} · {{ item.name }}</b><span class="account-name">{{ time(item.createdAt) }}</span></td><td><span class="account-status" :class="{ inactive: item.status !== 'ACTIVE' }">{{ schemeStatuses[item.status] }}</span></td><td>{{ item.dimensionCount }}个维度<span class="account-name">合计 {{ decimal(item.totalWeight) }}%</span></td><td>{{ decimal(item.threshold) }} 分</td><td><div class="account-actions"><button :disabled="loading" @click="view(item.id)">查看详情</button><template v-if="isAdmin"><button v-if="item.status === 'DRAFT'" :disabled="saving || loading" @click="edit(item.id)">编辑草稿</button><button v-if="item.status === 'DRAFT'" :disabled="loading" @click="prepareActivation(item.id)">启用</button><button :disabled="copying || loading" @click="copy(item)">复制为新草稿</button></template></div></td></tr></tbody></table><p v-if="loading" class="account-empty" role="status">正在读取方案…</p><p v-else-if="!result.items.length && !error" class="account-empty">暂无符合条件的评分方案。</p></div>
    <div class="account-pagination"><span>共 {{ result.total }} 条记录</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" :disabled="loading" @change="load()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="loading || result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span>{{ result.page }} / {{ pages }}</span><ElButton :disabled="loading || result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
  </section>
  <ElDialog v-model="editorOpen" :title="editorId ? '编辑评分草稿' : '新建评分草稿'" width="720px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <form class="form-stack" @submit.prevent="save"><p class="form-hint">草稿可反复调整。启用后作为固定版本保留，后续调整请复制新草稿。</p><div class="field"><label for="scheme-name">方案名称</label><ElInput id="scheme-name" v-model="form.name" required maxlength="64" :disabled="saving" /></div><div class="field"><label for="scheme-threshold">整改阈值</label><ElInput id="scheme-threshold" v-model="form.threshold" inputmode="decimal" required maxlength="6" :disabled="saving" /><p class="form-hint">成绩严格低于该值时需要整改；0–100，最多两位小数。</p></div>
      <fieldset class="scheme-dimensions" :disabled="saving"><legend>评分维度与权重</legend><div v-for="(dimension, index) in form.dimensions" :key="index" class="scheme-dimension"><div class="field"><label :for="`dimension-name-${index}`">维度名称 {{ index + 1 }}</label><ElInput :id="`dimension-name-${index}`" v-model="dimension.name" required maxlength="64" :disabled="saving" /></div><div class="field"><label :for="`dimension-weight-${index}`">权重 {{ index + 1 }}</label><ElInput :id="`dimension-weight-${index}`" v-model="dimension.weight" inputmode="decimal" required maxlength="7" :disabled="saving"><template #append>%</template></ElInput></div><ElButton :aria-label="`删除维度 ${index + 1}`" :disabled="saving || form.dimensions.length <= 1" @click="form.dimensions.splice(index, 1)">删除</ElButton></div><ElButton :disabled="saving || form.dimensions.length >= 20" @click="form.dimensions.push({ name: '', weight: '' })">添加维度</ElButton></fieldset>
      <p class="form-hint" data-testid="scheme-total">权重合计：{{ total === null ? '请填写有效权重' : `${decimal(total)}%` }}。可先保存草稿，启用时须为100%。每个权重0–100，最多两位小数；1–20个维度。</p>
      <p v-if="formError" class="message error" role="alert">{{ formError }}</p><button v-if="formError && editorId" type="button" class="text-button" :disabled="saving" @click="edit(editorId)">载入最新草稿（替换输入）</button>
      <div class="form-actions"><ElButton native-type="submit" :disabled="saving" :loading="saving">保存草稿</ElButton><ElButton :disabled="saving" @click="editorOpen = false">返回</ElButton></div>
    </form>
  </ElDialog>
  <ElDialog v-model="detailOpen" :title="`方案详情 #${detailId}`" width="700px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!copying" :show-close="!copying" destroy-on-close>
    <p v-if="detailLoading" role="status">正在读取详情…</p><p v-if="detailError" class="message error" role="alert">{{ detailError }} <button class="text-button" :disabled="detailLoading || copying" @click="view(detailId)">刷新详情</button></p>
    <template v-if="detail"><div class="repair-detail-head"><h2>{{ detail.name }}</h2><span class="account-status">{{ schemeStatuses[detail.status] }}</span></div><p class="form-hint">创建：{{ time(detail.createdAt) }}<template v-if="detail.activatedAt"><br />首次启用：{{ time(detail.activatedAt) }}</template><template v-if="detail.sourceId"><br />基于版本 #{{ detail.sourceId }} 复制</template></p><p data-testid="scheme-threshold">整改阈值：{{ decimal(detail.threshold) }} 分</p><table class="account-table"><caption class="sr-only">版本维度与权重</caption><thead><tr><th>维度</th><th>权重</th></tr></thead><tbody><tr v-for="d in detail.dimensions" :key="d.id"><td>{{ d.name }}</td><td>{{ decimal(d.weight) }}%</td></tr></tbody></table><p class="form-hint">合计 {{ decimal(detail.totalWeight) }}%。已启用及历史版本保留原定义，已有检查采用各自绑定的版本。</p></template>
    <div class="form-actions"><ElButton v-if="isAdmin && detail" :disabled="copying || detailLoading" :loading="copying" @click="copy(detail)">复制为新草稿</ElButton><ElButton :disabled="copying" @click="detailOpen = false">关闭详情</ElButton></div>
  </ElDialog>
  <ElDialog v-model="activationOpen" title="启用评分方案" width="620px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!activating" :show-close="!activating" destroy-on-close>
    <p v-if="activationLoading" role="status">正在核对启用信息…</p><template v-if="activation"><h2>#{{ activation.id }} · {{ activation.name }}</h2><p>权重合计 {{ decimal(activation.totalWeight) }}% · 整改阈值 {{ decimal(activation.threshold) }} 分</p><p v-if="previousActive" class="form-hint">当前：#{{ previousActive.id }} · {{ previousActive.name }}。原启用版本将转为历史，已有检查记录保持原标准。</p><p v-else class="form-hint">当前尚无启用方案。</p><p class="form-hint">确认后，此版本用于后续新检查批次；维度、权重和阈值将固定保留。</p></template>
    <p v-if="activationError" class="message error" role="alert">{{ activationError }} <button class="text-button" :disabled="activationLoading || activating" @click="prepareActivation(activationId)">刷新启用信息</button></p><div class="form-actions"><ElButton :disabled="activating || activationLoading || !activation || activation.status !== 'DRAFT'" :loading="activating" @click="activate">确认启用</ElButton><ElButton :disabled="activating" @click="activationOpen = false">返回</ElButton></div>
  </ElDialog>
</template>
