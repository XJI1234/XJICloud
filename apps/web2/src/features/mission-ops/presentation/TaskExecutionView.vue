<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const operatorSrc = computed(() => route.name === 'route-planning' ? '/sky-operator/index.html?workspace=routes' : '/sky-operator/index.html')
</script>

<template>
  <iframe class="desktop-operator" :src="operatorSrc" title="Sky Command 操作台"></iframe>
</template>

<style scoped>
.desktop-operator {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #f5f6f8;
}
</style>
