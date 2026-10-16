<script setup lang="ts">
import {
  PhAirplaneTilt as AirplaneTilt,
  PhCube as Cube,
  PhGlobe as Globe,
  PhHouse as House,
  PhMapTrifold as MapTrifold,
  PhPaperPlaneTilt as PaperPlaneTilt,
  PhPencilSimple as PencilSimple,
  PhQuestion as Question,
  PhUploadSimple as UploadSimple,
  PhUsers as Users,
} from '@phosphor-icons/vue'

const props = defineProps<{
  name: 'home' | 'route' | 'mission' | 'upload' | 'view' | 'edit' | 'team' | 'language' | 'help' | 'aircraft'
}>()

const icons = {
  aircraft: AirplaneTilt,
  home: House,
  route: MapTrifold,
  mission: PaperPlaneTilt,
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
