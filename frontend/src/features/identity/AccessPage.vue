<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '../../components/AppIcon.vue'
import { workspacePath } from './session'
</script>
<template><section class="card access-card"><span class="feature-icon"><AppIcon name="shield" /></span><h1>此页面暂不可访问</h1><p class="subtitle">页面不存在，或当前账号没有访问权限。</p><RouterLink :to="workspacePath()" class="text-button">返回个人工作台 <AppIcon name="arrow" /></RouterLink></section></template>
