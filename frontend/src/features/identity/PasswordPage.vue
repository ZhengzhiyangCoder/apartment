<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElInput } from 'element-plus'
import AppIcon from '../../components/AppIcon.vue'
import { HttpError, write } from '../../lib/http'
import { currentUser } from './session'

const router = useRouter()
const currentPassword = ref(''), newPassword = ref(''), confirmation = ref('')
const busy = ref(false), error = ref('')
async function submit() {
  if (busy.value) return
  error.value = ''
  if (newPassword.value !== confirmation.value) { error.value = '两次输入的新密码不一致'; return }
  if (new TextEncoder().encode(newPassword.value).length > 72) { error.value = '新密码过长，请减少字符后重试'; return }
  busy.value = true
  try {
    await write('/api/auth/password', JSON.stringify({ currentPassword: currentPassword.value, newPassword: newPassword.value }), 'application/json')
    currentPassword.value = ''; newPassword.value = ''; confirmation.value = ''
    currentUser.value = null
    await router.replace({ path: '/login', query: { changed: '1' } })
  } catch (failure) {
    if (failure instanceof HttpError && failure.status === 401) {
      currentUser.value = null; await router.replace('/login')
    } else { error.value = failure instanceof Error ? failure.message : '修改失败，请重试' }
  } finally { busy.value = false }
}
</script>
<template>
  <section class="page-head"><p class="eyebrow">ACCOUNT SECURITY / 账号安全</p><h1>修改密码</h1><p class="subtitle">更新登录密码，让个人空间多一份安心。</p></section>
  <section class="card password-card">
    <div class="card-intro"><span class="feature-icon"><AppIcon name="key" /></span><div><h2>设置新的登录密码</h2><p class="form-hint">更新成功后，请使用新密码重新登录。</p></div></div>
    <form class="form-stack" @submit.prevent="submit">
      <div class="field"><label for="current-password">当前密码</label><ElInput id="current-password" v-model="currentPassword" type="password" show-password autocomplete="current-password" :maxlength="64" required :disabled="busy" placeholder="请输入当前密码" /></div>
      <div class="field"><label for="new-password">新密码</label><ElInput id="new-password" v-model="newPassword" type="password" show-password autocomplete="new-password" :minlength="8" :maxlength="64" required :disabled="busy" placeholder="请输入 8–64 位新密码" /><p class="form-hint">建议混合使用字母、数字和符号，避免与其他账号共用。</p></div>
      <div class="field"><label for="confirm-password">确认新密码</label><ElInput id="confirm-password" v-model="confirmation" type="password" show-password autocomplete="new-password" :minlength="8" :maxlength="64" required :disabled="busy" placeholder="再次输入新密码" /></div>
      <p v-if="error" class="message error" role="alert">{{ error }}</p>
      <div class="form-actions"><ElButton native-type="submit" :loading="busy" :disabled="busy">更新密码</ElButton><span class="form-hint">修改后，已有登录将失效。</span></div>
    </form>
  </section>
</template>
