<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAppLocale } from '@/presentation/composables/useAppLocale'

const { t } = useI18n()
const { currentLocale, setLocale } = useAppLocale()

function toggleLocale() {
  setLocale(currentLocale.value === 'zh' ? 'en' : 'zh')
}
</script>

<template>
  <button
    class="locale-toggle"
    type="button"
    :aria-label="t('header.switchLanguage')"
    @click="toggleLocale"
  >
    <span class="locale-toggle__part" :class="{ 'is-on': currentLocale === 'zh' }">中</span>
    <span class="locale-toggle__slash" aria-hidden="true">/</span>
    <span class="locale-toggle__part" :class="{ 'is-on': currentLocale === 'en' }">EN</span>
  </button>
</template>
