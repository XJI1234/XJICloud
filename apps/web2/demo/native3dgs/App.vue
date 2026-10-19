<script setup lang="ts">
import { ref } from 'vue'
import Native3DGSViewer from '@/features/viewer/presentation/components/Native3DGSViewer.vue'
const file = ref<File | null>(null)
const active = ref<File | null>(null)
function open(next: File) { file.value = next }
function cancel() { file.value = active.value }
function closed() { file.value = null; active.value = null }
</script>
<template>
  <div class="viewer-demo">
    <Native3DGSViewer :file="file" @open-file="open" @loaded="active = $event" @cancel="cancel" @closed="closed" />
  </div>
</template>
<style>
html, body, #app { width: 100%; height: 100%; margin: 0; overflow: hidden; }
.viewer-demo { width: 100%; height: 100%; }
</style>
