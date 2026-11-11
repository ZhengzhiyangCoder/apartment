<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElInput } from 'element-plus'
import AppIcon from '../../components/AppIcon.vue'
import { login, workspacePath } from './session'

const route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), busy = ref(false)
const error = ref(route.query.service ? '暂时无法连接服务，请稍后重试' : '')
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    await login(username.value.trim(), password.value)
    password.value = ''
    await router.replace(workspacePath())
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '登录失败，请重试' }
  finally { busy.value = false }
}
</script>

<template>
  <main class="login-shell">
    <section class="login-story" aria-label="栖居介绍">
      <div class="brand"><span class="brand-symbol"><AppIcon name="building" /></span><div><b>栖居</b><small>高校宿舍管理系统</small></div></div>
      <div class="story-copy"><p class="eyebrow">CAMPUS LIVING · 校园生活</p><h2>熟悉的寝室，<br />安心的日常。</h2><p class="subtitle">把每一件日常小事，<br />安放在井然有序的校园生活里。</p></div>
      <div class="campus-art" aria-hidden="true">
        <svg viewBox="0 0 460 245"><path class="campus-ground" d="M30 220h400"/><path class="campus-building" d="M110 220V56h175v164M285 112h74v108M95 56h205M100 42h194v14"/><path class="campus-windows" d="M134 82h24v26h-24zM178 82h24v26h-24zM222 82h24v26h-24zM134 126h24v26h-24zM178 126h24v26h-24zM222 126h24v26h-24zM134 170h24v26h-24zM222 170h24v26h-24zM306 137h25v27h-25zM306 180h25v27h-25z"/><path class="campus-door" d="M176 220v-43h28v43"/><path class="campus-tree" d="M64 219v-52m0-49c-32 0-32 62 0 62s32-62 0-62ZM395 219v-45m0-41c-26 0-26 53 0 53s26-53 0-53Z"/></svg>
      </div>
      <p class="story-footer">一处栖居，一份安心。</p>
    </section>
    <section class="login-card card">
      <span class="feature-icon"><AppIcon name="user" /></span>
      <p class="eyebrow">WELCOME BACK</p><h1>登录栖居</h1><p class="subtitle">使用你的账号，进入个人工作台。</p>
      <form class="form-stack" @submit.prevent="submit">
        <div class="field"><label for="login-username">账号</label><ElInput id="login-username" v-model="username" autocomplete="username" placeholder="请输入账号" :maxlength="64" required :disabled="busy" /></div>
        <div class="field"><label for="login-password">密码</label><ElInput id="login-password" v-model="password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" :maxlength="64" required :disabled="busy" /></div>
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <p v-if="route.query.changed" class="message success" role="status">密码已更新，请使用新密码登录。</p>
        <p v-if="route.query.accountChanged" class="message success" role="status">账号状态或权限已更新，请重新登录。</p>
        <ElButton class="login-submit" native-type="submit" :loading="busy" :disabled="busy">登录 <AppIcon name="arrow" /></ElButton>
      </form>
      <p class="login-help"><AppIcon name="shield" />账号由系统管理员创建。<br />忘记密码时，请联系系统管理员。</p>
    </section>
  </main>
</template>
