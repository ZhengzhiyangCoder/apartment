<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { request, write } from '../../lib/http'
import { currentUser, handleAuthorizationFailure } from '../identity/session'
import CatalogPicker from './CatalogPicker.vue'
import type { CatalogItem, CatalogKind, Building, Room, PageResult } from './types'

const names: Record<CatalogKind, string> = { buildings: '楼栋', rooms: '寝室', beds: '床位', students: '学生资料' }
const kind = ref<CatalogKind>('buildings'), router = useRouter()
const canEdit = computed(() => currentUser.value?.role === 'SYSTEM_ADMIN')
const tabs = computed(() => (Object.keys(names) as CatalogKind[]).filter(key => canEdit.value || key !== 'students'))
const query = reactive({ q: '', enabled: '', size: 10 })
const scope = ref<{ id: number; label: string; param: 'buildingId' | 'roomId' } | null>(null)
const result = ref<PageResult<CatalogItem>>({ items: [], total: 0, page: 1, size: 10 })
const loading = ref(false), error = ref(''), success = ref('')
const open = ref(false), saving = ref(false), formError = ref('')
const target = ref<CatalogItem | null>(null), action = ref<'create' | 'edit' | 'enabled'>('create')
const form = reactive({ parentId: null as number | null, name: '', roomNo: '', floor: 1, bedNo: '', userId: null as number | null, studentNo: '', fullName: '', college: '', major: '', className: '' })
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))
const title = computed(() => `${action.value === 'create' ? '新增' : action.value === 'edit' ? '编辑' : target.value?.enabled ? '停用' : '启用'}${names[kind.value]}`)
const parentLabel = computed(() => kind.value === 'rooms' ? '所属楼栋' : '所属寝室')
function itemLabel(item: CatalogItem) {
  if ('name' in item) return item.name
  if ('studentNo' in item) return `${item.fullName} · ${item.studentNo}`
  return `${item.buildingName} · ${item.roomNo}${'bedNo' in item ? ` · ${item.bedNo} 号床` : ''}`
}
const authorization = (failure: unknown) => handleAuthorizationFailure(failure, router)

const route = useRoute(), record = ref(String(route.query.record ?? ''))
async function leaveRecord() { record.value = ''; await router.replace({ path: route.path }); await load() }

async function load(page = 1) {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    if (record.value) {
      const item = await request<CatalogItem>(`/api/housing/${kind.value}/${encodeURIComponent(record.value)}`)
      result.value = { items: [item], total: 1, page: 1, size: 10 }
      return
    }
    const params = new URLSearchParams({ q: query.q, page: String(page), size: String(query.size) })
    if (query.enabled) params.set('enabled', query.enabled)
    if (scope.value) params.set(scope.value.param, String(scope.value.id))
    let data = await request<PageResult<CatalogItem>>(`/api/housing/${kind.value}?${params}`)
    const last = Math.max(1, Math.ceil(data.total / data.size))
    if (page > last) { params.set('page', String(last)); data = await request<PageResult<CatalogItem>>(`/api/housing/${kind.value}?${params}`) }
    result.value = data
  } catch (failure) { if (!await authorization(failure)) error.value = failure instanceof Error ? failure.message : '加载失败，请重试' }
  finally { loading.value = false }
}
function switchTab(tab: CatalogKind) {
  if (loading.value) return
  record.value = ''; void router.replace({ path: '/housing' }); kind.value = tab; scope.value = null; query.q = ''; query.enabled = ''; success.value = ''; result.value.items = []; load()
}
function children(item: Building | Room) {
  record.value = ''; void router.replace({ path: '/housing' }); kind.value = 'name' in item ? 'rooms' : 'beds'
  scope.value = { id: item.id, label: itemLabel(item), param: 'name' in item ? 'buildingId' : 'roomId' }
  query.q = ''; query.enabled = ''; success.value = ''; result.value.items = []; load()
}
function edit(mode: 'create' | 'edit' | 'enabled', item: CatalogItem | null = null) {
  target.value = item; action.value = mode; formError.value = ''; success.value = ''
  Object.assign(form, { parentId: scope.value?.id ?? null, name: '', roomNo: '', floor: 1, bedNo: '', userId: null, studentNo: '', fullName: '', college: '', major: '', className: '' })
  if (item && 'name' in item) form.name = item.name
  if (item && 'roomNo' in item) { form.roomNo = item.roomNo; if ('floor' in item) form.floor = item.floor }
  if (item && 'bedNo' in item) form.bedNo = item.bedNo
  if (item && 'studentNo' in item) Object.assign(form, { userId: item.userId, studentNo: item.studentNo, fullName: item.fullName, college: item.college, major: item.major, className: item.className })
  open.value = true
}
async function submit() {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try {
    const version = target.value?.version
    let body: object
    if (action.value === 'enabled') body = { enabled: !target.value!.enabled, version }
    else if (kind.value === 'buildings') body = { name: form.name, version }
    else if (kind.value === 'rooms') body = { roomNo: form.roomNo, floor: form.floor, ...(action.value === 'create' ? { buildingId: form.parentId } : { version }) }
    else if (kind.value === 'beds') body = { bedNo: form.bedNo, ...(action.value === 'create' ? { roomId: form.parentId } : { version }) }
    else body = { studentNo: form.studentNo, fullName: form.fullName, college: form.college, major: form.major, className: form.className, ...(action.value === 'create' ? { userId: form.userId } : { version }) }
    const path = `/api/housing/${kind.value}${target.value ? `/${target.value.id}` : ''}${action.value === 'enabled' ? '/enabled' : ''}`
    await write<CatalogItem>(path, JSON.stringify(body), 'application/json')
    success.value = `${title.value}成功`; open.value = false
    await load(action.value === 'create' ? 1 : result.value.page)
  } catch (failure) { if (!await authorization(failure)) formError.value = failure instanceof Error ? failure.message : '保存失败，请重试' }
  finally { saving.value = false }
}
onMounted(() => {
  const requested = String(route.query.kind ?? '')
  if (requested && tabs.value.includes(requested as CatalogKind)) kind.value = requested as CatalogKind
  void load()
})
</script>

<template>
  <section class="page-head account-heading">
    <div><p class="eyebrow">HOUSING / 住宿管理</p><h1>住宿管理</h1><p class="subtitle">从一栋楼到一张床，让校园住宿资料清晰有序。</p></div>
    <ElButton v-if="canEdit" :disabled="loading" @click="edit('create')">新增{{ names[kind] }}</ElButton><span v-else class="role-badge">资料只读</span>
  </section>
  <div class="housing-tabs" role="tablist" aria-label="住宿资料分类"><button v-for="tab in tabs" :key="tab" role="tab" :aria-selected="kind === tab" :disabled="loading" @click="switchTab(tab)">{{ names[tab] }}</button></div>
  <section class="card account-card">
    <div v-if="scope" class="housing-scope"><span>{{ scope.label }} <b>/ {{ names[kind] }}</b></span><button class="text-button" :disabled="loading" @click="scope = null; load()">查看全部{{ names[kind] }}</button></div>
    <p v-if="record" class="housing-scope"><span>已定位记录 #{{ record }}</span><button class="text-button" :disabled="loading" @click="leaveRecord">退出记录定位</button></p>
    <form v-else class="account-filters" @submit.prevent="load(1)">
      <div class="field search-field"><label for="housing-query">搜索资料</label><ElInput id="housing-query" v-model="query.q" :placeholder="kind === 'students' ? '学号、姓名、学院或班级' : '楼栋名称、房号或床位编号'" :maxlength="64" :disabled="loading" /></div>
      <div class="field"><label for="housing-status">状态筛选</label><select id="housing-status" v-model="query.enabled" :disabled="loading"><option value="">全部状态</option><option value="true">已启用</option><option value="false">已停用</option></select></div>
      <ElButton native-type="submit" :disabled="loading" :loading="loading">查询</ElButton>
    </form>
    <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load(result.page)">重新加载</button></p>
    <p v-if="success" class="message success" role="status">{{ success }}</p>
    <div class="account-table-wrap" :aria-busy="loading"><table class="account-table housing-table"><caption class="sr-only">{{ names[kind] }}列表</caption>
      <thead><tr><th scope="col">{{ names[kind] }}</th><th scope="col">{{ kind === 'students' ? '学籍信息' : '归属信息' }}</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
      <tbody><tr v-for="item in result.items" :key="item.id">
        <template v-if="'name' in item"><td><b>{{ item.name }}</b><span class="account-name">楼栋资料</span></td><td>校园楼栋</td></template>
        <template v-else-if="'studentNo' in item"><td><b>{{ item.fullName }}</b><span class="account-name">学号 {{ item.studentNo }}</span><span class="account-name">账号 {{ item.username }}{{ item.accountEnabled ? '' : '（已停用）' }}</span></td><td>{{ item.college }}<span class="account-name">{{ item.major }} · {{ item.className }}</span></td></template>
        <template v-else><td><b>{{ 'bedNo' in item ? `${item.bedNo} 号床` : item.roomNo }}</b><span v-if="'floor' in item" class="account-name">{{ item.floor }} 层</span></td><td>{{ item.buildingName }}<span class="account-name">{{ 'bedNo' in item ? `${item.roomNo} 寝室` : '所属楼栋' }}</span><span v-if="!item.buildingEnabled || ('roomEnabled' in item && !item.roomEnabled)" class="account-name parent-disabled">上级资料已停用</span></td></template>
        <td><span class="account-status" :class="{ inactive: !item.enabled }">{{ item.enabled ? '已启用' : '已停用' }}</span></td>
        <td><div class="account-actions"><button v-if="'name' in item || 'floor' in item" :disabled="loading" @click="children(item)">{{ 'name' in item ? '查看寝室' : '查看床位' }}</button><template v-if="canEdit"><button :disabled="loading" @click="edit('edit', item)">编辑</button><button :class="{ danger: item.enabled }" :disabled="loading" @click="edit('enabled', item)">{{ item.enabled ? '停用' : '启用' }}</button></template><span v-else-if="'bedNo' in item" class="form-hint">只读</span></div></td>
      </tr></tbody>
    </table><p v-if="loading" role="status" class="account-empty">正在读取资料…</p><p v-else-if="!result.items.length && !error" class="account-empty">暂无符合条件的{{ names[kind] }}，请调整查询条件{{ canEdit ? '或新增资料' : '' }}。</p></div>
    <div v-if="!record" class="account-pagination"><span>共 {{ result.total }} 条{{ names[kind] }}</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" :disabled="loading" @change="load()"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="loading || result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span aria-live="polite">{{ result.page }} / {{ pages }}</span><ElButton :disabled="loading || result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
  </section>
  <p class="quiet-note">{{ kind === 'students' ? '学籍资料与登录账号分别维护；关联账号创建后固定，历史资料始终保留。' : '停用资料保留历史；新建或启用下级前，请先启用上级资料。' }}</p>

  <ElDialog v-model="open" :title="title" width="600px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <form class="form-stack" @submit.prevent="submit">
      <p v-if="target" class="form-hint">{{ itemLabel(target) }}</p>
      <template v-if="action === 'enabled'"><p class="message" :class="target?.enabled ? 'error' : 'success'">{{ target?.enabled ? '停用后保留资料及历史记录。已有下级资料不会被删除或自动改为停用。' : '确认重新启用这条资料。上级资料和关联账号须处于可用状态。' }}</p></template>
      <template v-else>
        <div v-if="kind === 'buildings'" class="field"><label for="building-name">楼栋名称</label><ElInput id="building-name" v-model="form.name" required maxlength="64" :disabled="saving" /></div>
        <template v-if="kind === 'rooms' || kind === 'beds'">
          <template v-if="action === 'create'"><p v-if="scope" class="form-hint">{{ parentLabel }}：{{ scope.label }}</p><CatalogPicker v-else v-model="form.parentId" :source="kind === 'rooms' ? 'buildings' : 'rooms'" :label="parentLabel" :disabled="saving" @failure="authorization" /></template>
          <div v-if="kind === 'rooms'" class="form-grid"><div class="field"><label for="room-no">房号</label><ElInput id="room-no" v-model="form.roomNo" required maxlength="32" :disabled="saving" /></div><div class="field"><label for="room-floor">楼层</label><ElInput id="room-floor" v-model.number="form.floor" type="number" required min="1" max="100" step="1" :disabled="saving" /></div></div>
          <div v-else class="field"><label for="bed-no">床位编号</label><ElInput id="bed-no" v-model="form.bedNo" required maxlength="32" :disabled="saving" /></div>
          <p class="form-hint">{{ kind === 'rooms' ? '同楼栋房号唯一；所属楼栋创建后固定。' : '同寝室床位编号唯一；所属寝室创建后固定。' }}</p>
        </template>
        <template v-if="kind === 'students'">
          <CatalogPicker v-if="action === 'create'" v-model="form.userId" source="accounts" label="关联学生账号" :disabled="saving" @failure="authorization" />
          <p v-if="action === 'create'" class="form-hint">请先在账号管理创建学生账号；一个账号仅能关联一条学生资料。</p>
          <div class="form-grid"><div class="field"><label for="student-no">学号</label><ElInput id="student-no" v-model="form.studentNo" required maxlength="32" :disabled="saving" /></div><div class="field"><label for="student-name">学生姓名</label><ElInput id="student-name" v-model="form.fullName" required maxlength="64" :disabled="saving" /></div></div>
          <div class="field"><label for="student-college">学院</label><ElInput id="student-college" v-model="form.college" required maxlength="64" :disabled="saving" /></div>
          <div class="form-grid"><div class="field"><label for="student-major">专业</label><ElInput id="student-major" v-model="form.major" required maxlength="64" :disabled="saving" /></div><div class="field"><label for="student-class">班级</label><ElInput id="student-class" v-model="form.className" required maxlength="64" :disabled="saving" /></div></div>
        </template>
      </template>
      <p v-if="formError" class="message error" role="alert">{{ formError }}</p>
      <div class="form-actions"><ElButton native-type="submit" :loading="saving" :disabled="saving">{{ action === 'enabled' ? (target?.enabled ? '确认停用' : '确认启用') : '保存' }}</ElButton><ElButton :disabled="saving" @click="open = false">取消</ElButton></div>
    </form>
  </ElDialog>
</template>
