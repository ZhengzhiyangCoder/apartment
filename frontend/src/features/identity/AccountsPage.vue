<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { HttpError, request, write } from '../../lib/http'
import { currentUser, refreshUser, type Role } from './session'

interface Account { id: number; username: string; displayName: string; role: Role; enabled: boolean; version: number }
interface AccountPage { items: Account[]; total: number; page: number; size: number }
type Action = 'create' | 'role' | 'reset-password' | 'enabled'
const roles: Record<Role, string> = { SYSTEM_ADMIN: '系统管理员', DORM_MANAGER: '宿舍管理员', STUDENT: '学生', MAINTAINER: '维修人员' }
const router = useRouter()
const query = reactive({ q: '', role: '', enabled: '', size: 10 })
const result = ref<AccountPage>({ items: [], total: 0, page: 1, size: 10 })
const loading = ref(false), error = ref(''), success = ref('')
const open = ref(false), saving = ref(false), formError = ref('')
const action = ref<Action>('create'), target = ref<Account | null>(null)
const form = reactive({ username: '', displayName: '', role: 'STUDENT' as Role, password: '', confirmation: '' })
const titles = computed(() => ({ create: '创建账号', role: '变更角色', 'reset-password': '重置密码', enabled: target.value?.enabled ? '禁用账号' : '启用账号' }))
const submitLabel = computed(() => ({ create: '确认创建', role: '保存角色', 'reset-password': '确认重置', enabled: target.value?.enabled ? '确认禁用' : '确认启用' })[action.value])
const pages = computed(() => Math.max(1, Math.ceil(result.value.total / result.value.size)))

async function handleAuthorization(failure: unknown) {
  if (failure instanceof HttpError && failure.status === 401) {
    currentUser.value = null; await router.replace({ path: '/login', query: { accountChanged: '1' } }); return true
  }
  if (failure instanceof HttpError && failure.status === 403) { await router.replace('/forbidden'); return true }
  return false
}

async function load(page = 1) {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ q: query.q, page: String(page), size: String(query.size) })
    if (query.role) params.set('role', query.role)
    if (query.enabled) params.set('enabled', query.enabled)
    let data = await request<AccountPage>(`/api/identity/accounts?${params}`)
    const lastPage = Math.max(1, Math.ceil(data.total / data.size))
    if (page > lastPage) {
      params.set('page', String(lastPage))
      data = await request<AccountPage>(`/api/identity/accounts?${params}`)
    }
    result.value = data
  } catch (failure) {
    if (!await handleAuthorization(failure)) error.value = failure instanceof Error ? failure.message : '加载失败，请重试'
  } finally { loading.value = false }
}

function edit(kind: Action, account: Account | null = null) {
  action.value = kind; target.value = account; formError.value = ''; success.value = ''
  Object.assign(form, { username: '', displayName: '', role: account?.role ?? 'STUDENT', password: '', confirmation: '' })
  open.value = true
}

async function submit() {
  if (saving.value) return
  formError.value = ''
  if (action.value === 'reset-password' && form.password !== form.confirmation) { formError.value = '两次输入的新密码不一致'; return }
  if (new TextEncoder().encode(form.password).length > 72) { formError.value = '密码过长，请减少字符后重试'; return }
  saving.value = true
  const account = target.value
  try {
    const body = action.value === 'create'
      ? { username: form.username, displayName: form.displayName, role: form.role, password: form.password }
      : action.value === 'role' ? { role: form.role, version: account!.version }
      : action.value === 'reset-password' ? { newPassword: form.password, version: account!.version }
      : { enabled: !account!.enabled, version: account!.version }
    const path = action.value === 'create' ? '/api/identity/accounts' : `/api/identity/accounts/${account!.id}/${action.value}`
    await write<Account>(path, JSON.stringify(body), 'application/json')
    success.value = `${titles.value[action.value]}成功`
    form.password = ''; form.confirmation = ''; open.value = false
    if (account?.id === currentUser.value?.id) {
      await refreshUser()
      if (!currentUser.value) { await router.replace({ path: '/login', query: { accountChanged: '1' } }); return }
    }
    await load(action.value === 'create' ? 1 : result.value.page)
  } catch (failure) {
    if (!await handleAuthorization(failure)) formError.value = failure instanceof Error ? failure.message : '保存失败，请重试'
  } finally { saving.value = false }
}
onMounted(() => load())
</script>

<template>
  <section class="page-head account-heading">
    <div><p class="eyebrow">ACCOUNT MANAGEMENT / 系统管理</p><h1>账号管理</h1><p class="subtitle">管理校园成员的账号、角色与使用权限。</p></div>
    <ElButton :disabled="loading" @click="edit('create')">创建账号</ElButton>
  </section>
  <section class="card account-card">
    <form class="account-filters" @submit.prevent="load(1)">
      <div class="field search-field"><label for="account-query">搜索账号或姓名</label><ElInput id="account-query" v-model="query.q" placeholder="输入账号或姓名" :maxlength="64" :disabled="loading" /></div>
      <div class="field"><label for="role-filter">角色筛选</label><select id="role-filter" v-model="query.role" :disabled="loading"><option value="">全部角色</option><option v-for="(label, role) in roles" :key="role" :value="role">{{ label }}</option></select></div>
      <div class="field"><label for="status-filter">状态筛选</label><select id="status-filter" v-model="query.enabled" :disabled="loading"><option value="">全部状态</option><option value="true">已启用</option><option value="false">已停用</option></select></div>
      <ElButton native-type="submit" :loading="loading" :disabled="loading">查询</ElButton>
    </form>
    <p v-if="error" class="message error" role="alert">{{ error }} <button class="text-button" @click="load(result.page)">重新加载</button></p>
    <p v-if="success" class="message success" role="status">{{ success }}</p>
    <div class="account-table-wrap" :aria-busy="loading">
      <table class="account-table"><caption class="sr-only">系统账号列表</caption>
        <thead><tr><th scope="col">账号 / 姓名</th><th scope="col">角色</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
        <tbody><tr v-for="account in result.items" :key="account.id">
          <td><b>{{ account.username }}</b><span class="account-name">{{ account.displayName }} <small v-if="account.id === currentUser?.id">（本人）</small></span></td>
          <td>{{ roles[account.role] }}</td><td><span class="account-status" :class="{ inactive: !account.enabled }">{{ account.enabled ? '已启用' : '已停用' }}</span></td>
          <td><div class="account-actions"><button :disabled="loading" @click="edit('role', account)">变更角色</button><button :disabled="loading" @click="edit('reset-password', account)">重置密码</button><button :disabled="loading" :class="{ danger: account.enabled }" @click="edit('enabled', account)">{{ account.enabled ? '禁用' : '启用' }}</button></div></td>
        </tr></tbody>
      </table>
      <p v-if="!result.items.length && !loading && !error" class="account-empty">暂无符合条件的账号，请调整查询条件。</p>
      <p v-if="loading" class="account-empty" role="status">正在读取账号…</p>
    </div>
    <div class="account-pagination"><span>共 {{ result.total }} 个账号</span><label>每页 <select v-model.number="query.size" aria-label="每页条数" :disabled="loading" @change="load(1)"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label><div><ElButton :disabled="loading || result.page <= 1" @click="load(result.page - 1)">上一页</ElButton><span aria-live="polite">{{ result.page }} / {{ pages }}</span><ElButton :disabled="loading || result.page >= pages" @click="load(result.page + 1)">下一页</ElButton></div></div>
  </section>
  <p class="quiet-note">账号停用后保留历史记录；角色和安全设置变化后，相关账号需重新登录。</p>

  <ElDialog v-model="open" :title="titles[action]" width="540px" class="account-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close @closed="form.password = ''; form.confirmation = ''">
    <form class="form-stack" @submit.prevent="submit">
      <p v-if="target" class="form-hint">{{ target.displayName }} · {{ target.username }}</p>
      <template v-if="action === 'create'">
        <div class="field"><label for="create-username">登录账号</label><ElInput id="create-username" v-model="form.username" required minlength="3" maxlength="64" pattern="[A-Za-z0-9_.\-]{3,64}" autocomplete="off" :disabled="saving" /><p class="form-hint">3–64 位字母、数字、下划线、点或短横线；创建后固定。</p></div>
        <div class="field"><label for="create-name">姓名</label><ElInput id="create-name" v-model="form.displayName" required maxlength="64" :disabled="saving" /></div>
      </template>
      <div v-if="action === 'create' || action === 'role'" class="field"><label for="edit-role">角色</label><select id="edit-role" v-model="form.role" :disabled="saving"><option v-for="(label, role) in roles" :key="role" :value="role">{{ label }}</option></select></div>
      <template v-if="action === 'create' || action === 'reset-password'">
        <div class="field"><label for="managed-password">{{ action === 'create' ? '初始密码' : '新密码' }}</label><ElInput id="managed-password" v-model="form.password" type="password" show-password autocomplete="new-password" required minlength="8" maxlength="64" :disabled="saving" /><p class="form-hint">8–64 位，建议混合字母、数字和符号。</p></div>
        <div v-if="action === 'reset-password'" class="field"><label for="managed-confirmation">确认新密码</label><ElInput id="managed-confirmation" v-model="form.confirmation" type="password" show-password autocomplete="new-password" required minlength="8" maxlength="64" :disabled="saving" /></div>
      </template>
      <p v-if="action === 'enabled'" class="message" :class="target?.enabled ? 'error' : 'success'">{{ target?.enabled ? '禁用后无法登录，已有登录将失效。历史记录会保留。' : '启用后可使用现有密码重新登录。' }}</p>
      <p v-if="action === 'role' || action === 'reset-password'" class="form-hint">保存后已有登录将失效，请告知该成员重新登录。</p>
      <p v-if="formError" class="message error" role="alert">{{ formError }}</p>
      <div class="form-actions"><ElButton native-type="submit" :loading="saving" :disabled="saving">{{ submitLabel }}</ElButton><ElButton :disabled="saving" @click="open = false">取消</ElButton></div>
    </form>
  </ElDialog>
</template>
