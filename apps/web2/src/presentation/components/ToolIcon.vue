<script setup lang="ts">
import {
  PhCube as Cube,
  PhGlobe as Globe,
  PhHouse as House,
  PhMapTrifold as MapTrifold,
  PhPencilSimple as PencilSimple,
  PhQuestion as Question,
  PhUploadSimple as UploadSimple,
  PhUsers as Users,
} from '@phosphor-icons/vue'

const props = defineProps<{
  name: 'home' | 'route' | 'upload' | 'view' | 'edit' | 'team' | 'language' | 'help'
}>()

const icons = {
  home: House,
  route: MapTrifold,
  upload: UploadSimple,
  view: Cube,
  edit: PencilSimple,
  team: Users,
  language: Globe,
  help: Question,
}
</script>

<template>
  <component :is="icons[props.name]" :size="20" :weight="props.name === 'home' ? 'fill' : 'regular'" />
</template>
