<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { HttpError, request, write } from '../../lib/http'
import { currentUser } from '../identity/session'
import CatalogPicker from './CatalogPicker.vue'
import type { PageResult, Residency } from './types'

const router = useRouter(), query = reactive({ q: '', current: 'true', size: 10 })
const result = ref<PageResult<Residency>>({ items: [], total: 0, page: 1, size: 10 })
const loading = ref(false), error = ref(''), success = ref(''), open = ref(false), saving = ref(false), formError = ref('')
const form = reactive({ studentId: null as number | null, bedId: null as number | null, note: '' })
const action = ref<'checkin' | 'transfer' | 'checkout'>('checkin'), target = ref<Residency | null>(null)
const titles = { checkin: '办理入住', transfer: '办理调寝', checkout: '办理退宿' }
const confirms = { checkin: '确认入住', transfer: '确认调寝', checkout: '确认退宿' }
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))
async function authorization(failure: unknown) {
  if (failure instanceof HttpError && failure.status === 401) { currentUser.value = null; await router.replace('/login'); return true }
  if (failure instanceof HttpError && failure.status === 403) { await router.replace('/forbidden'); return true }
  return false
}
async function load(page = 1) {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ q: query.q, page: String(page), size: String(query.size) })
    if (query.current) params.set('current', query.current)
    let data = await request<PageResult<Residency>>(`/api/housing/residencies?${params}`)
    const last = Math.max(1, Math.ceil(data.total / data.size))
    if (page > last) { params.set('page', String(last)); data = await request<PageResult<Residency>>(`/api/housing/residencies?${params}`) }
    result.value = data
  } catch (failure) { if (!await authorization(failure)) error.value = failure instanceof Error ? failure.message : '读取失败，请重试' }
  finally { loading.value = false }
}
function edit(mode: 'checkin' | 'transfer' | 'checkout', item: Residency | null = null) {
  action.value = mode; target.value = item; form.studentId = null; form.bedId = null; form.note = ''
  formError.value = ''; success.value = ''; open.value = true
}
async function submit() {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try {
    const path = `/api/housing/residencies${target.value ? `/${target.value.id}/${action.value}` : ''}`
    const body = action.value === 'checkin' ? { studentId: form.studentId, bedId: form.bedId } : { bedId: form.bedId, note: form.note }
    await write(path, JSON.stringify(body), 'application/json')
    open.value = false; success.value = `${titles[action.value]}成功`; await load(result.value.page)
  } catch (failure) { if (!await authorization(failure)) formError.value = failure instanceof Error ? failure.message : '办理失败，请重试' }
  finally { saving.value = false }
}
onMounted(() => load())
</script>
<template>
  <section class="page-head account-heading"><div><p class="eyebrow">RESIDENCY / 入住办理</p><h1>入住办理</h1><p class="subtitle">安排每一次入住，留存每一段校园生活。</p></div><ElButton :disabled="loading" @click="edit('checkin')">办理入住</ElButton></section>
  <section class="card account-card">
    <form class="account-filters" @submit.prevent="load()"><div class="field search-field"><label for="residency-q">搜索住宿</label><ElInput id="residency-q" v-model="query.q" placeholder="学号、姓名、楼栋或房号" maxlength="64" :disabled="loading" /></div><div class="field"><label for="residency-current">住宿状态</label><select id="residency-current" v-model="query.current" :disabled="loading"><option value="true">当前住宿</option><option value="false">历史住宿</option><option value="">全部记录</option></select></div><ElButton native-type="submit" :loading="loading" :disabled="loading">查询</ElButton></form>
    <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load(result.page)">重新加载</button></p><p v-if="success" class="message success" role="status">{{ success }}</p>
    <div class="account-table-wrap" :aria-busy="loading"><table class="account-table housing-table"><caption class="sr-only">住宿记录</caption><thead><tr><th>学生</th><th>住宿位置</th><th>状态</th><th>时间与操作</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.id"><td><b>{{ item.fullName }}</b><span class="account-name">{{ item.studentNo }}</span></td><td>{{ item.buildingName }}<span class="account-name">{{ item.roomNo }} · {{ item.bedNo }} 号床</span></td><td><span class="account-status" :class="{ inactive: item.endedAt }">{{ item.endedAt ? (item.endReason === 'TRANSFER' ? '已调寝' : '已退宿') : '在住' }}</span></td><td><span class="account-name">入住 {{ new Date(item.startedAt).toLocaleString('zh-CN') }}</span><span v-if="item.endedAt" class="account-name">结束 {{ new Date(item.endedAt).toLocaleString('zh-CN') }}<br />{{ item.endNote }}</span><div v-else class="account-actions"><button :disabled="loading" @click="edit('transfer', item)">调寝</button><button class="danger" :disabled="loading" @click="edit('checkout', item)">退宿</button></div></td></tr></tbody></table><p v-if="loading" class="account-empty" role="status">正在读取住宿记录…</p><p v-else-if="!result.items.length && !error" class="account-empty">暂无符合条件的住宿记录。</p></div>
    <div class="account-pagination"><span>共 {{ result.total }} 条记录</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" :disabled="loading" @change="load()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="loading || result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span>{{ result.page }} / {{ pages }}</span><ElButton :disabled="loading || result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
  </section>
  <p class="quiet-note">历史住宿持续保留；学生及床位不能重复分配。</p>
  <ElDialog v-model="open" :title="titles[action]" width="620px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close><form class="form-stack" @submit.prevent="submit"><p v-if="target" class="form-hint">{{ target.fullName }} · {{ target.studentNo }}<br />原住宿：{{ target.buildingName }} · {{ target.roomNo }} · {{ target.bedNo }} 号床</p><p v-if="action === 'checkout'" class="message error">确认退宿后释放当前床位，保留住宿历史。学生将暂时无法新建寝室报修或查看寝室成绩与排行。</p><CatalogPicker v-if="action === 'checkin'" v-model="form.studentId" source="students" label="入住学生" :disabled="saving" @failure="authorization" /><CatalogPicker v-if="action !== 'checkout'" v-model="form.bedId" source="vacant-beds" label="空床位" :disabled="saving" @failure="authorization" /><div v-if="action !== 'checkin'" class="field"><label for="residency-note">办理说明</label><ElInput id="residency-note" v-model="form.note" type="textarea" :rows="3" required maxlength="200" :disabled="saving" placeholder="填写本次办理原因，最多 200 字" /></div><p v-if="action === 'checkin'" class="form-hint">请选择尚未入住的有效学生；空床位以提交时的实际状态为准。</p><p v-if="formError" class="message error" role="alert">{{ formError }}</p><div class="form-actions"><ElButton native-type="submit" :loading="saving" :disabled="saving">{{ confirms[action] }}</ElButton><ElButton :disabled="saving" @click="open = false">取消</ElButton></div></form></ElDialog>
</template>
