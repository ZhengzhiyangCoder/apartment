<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{ url: string; index: number }>()
const failed = ref(false), attempt = ref(0)
function retry() { failed.value = false; attempt.value++ }
</script>
<template><figure class="repair-image"><img v-if="!failed" :src="`${props.url}?attempt=${attempt}`" :alt="`报修图片 ${index + 1}`" @error="failed = true" /><figcaption v-else role="alert">图片加载失败。<button class="text-button" @click="retry">重试图片 {{ index + 1 }}</button></figcaption></figure></template>
