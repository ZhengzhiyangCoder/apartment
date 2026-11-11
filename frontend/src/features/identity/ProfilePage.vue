<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '../../components/AppIcon.vue'
import { currentUser } from './session'
</script>
<template>
  <section class="page-head"><p class="eyebrow">MY PROFILE / 个人资料</p><h1>认识自己的一方空间</h1><p class="subtitle">查看当前账号的身份与基本信息。</p></section>
  <section class="card profile-card" v-if="currentUser">
    <div class="profile-heading"><span class="profile-avatar"><AppIcon name="user" /></span><div><h2>{{ currentUser.displayName }}</h2><p class="subtitle">{{ currentUser.roleLabel }}</p></div><span class="role-badge">当前账号</span></div>
    <dl class="profile-details"><div><dt>账号</dt><dd data-testid="profile-username">{{ currentUser.username }}</dd></div><div><dt>姓名</dt><dd>{{ currentUser.displayName }}</dd></div><div><dt>角色</dt><dd>{{ currentUser.roleLabel }}</dd></div></dl>
    <div class="profile-footer"><p>身份信息如有误，请联系系统管理员。</p><RouterLink class="text-button" to="/password">修改密码 <AppIcon name="arrow" /></RouterLink></div>
  </section>
</template>
