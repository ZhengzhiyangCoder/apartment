<script setup lang="ts">
const props = defineProps<{ name: 'building' | 'home' | 'user' | 'key' | 'logout' | 'arrow' | 'shield' }>()
const paths = {
  building: 'M5 21V3h14v18M3 21h18M10 21v-6h4v6M9 7h1m4 0h1M9 11h1m4 0h1',
  home: 'm3 10 9-7 9 7M5 9v12h14V9M10 21v-7h4v7',
  user: 'M20 21v-2a6 6 0 0 0-6-6h-4a6 6 0 0 0-6 6v2M16 6a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  key: 'M14 6a5 5 0 1 1-4 8l-7 7-2-2 7-7a5 5 0 0 1 6-6ZM15 9h.01',
  logout: 'M9 3H4v18h5M10 12h11m-4-4 4 4-4 4',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Zm-4 9 3 3 5-5',
}
</script>
<template><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path :d="paths[props.name]" /></svg></template>
