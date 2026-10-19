<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="help-page">
    <div class="cloud-page-inner">
      <h2 class="help-title">{{ t('help.title') }}</h2>
      <p class="help-subtitle">{{ t('help.subtitle') }}</p>
      <div class="help-layout">
        <section class="cloud-card">
          <h3 class="section-title">{{ t('help.gettingStarted') }}</h3>
          <ol class="help-list">
            <li>{{ t('help.stepRegister') }}</li>
            <li>{{ t('help.stepProject') }}</li>
            <li>{{ t('help.stepUpload') }}</li>
            <li>{{ t('help.stepView') }}</li>
          </ol>
          <h3 class="section-title">{{ t('help.trainingTitle') }}</h3>
          <p class="help-text">{{ t('help.trainingDesc') }}</p>
          <ul class="help-list">
            <li>{{ t('help.trainingItem1') }}</li>
            <li>{{ t('help.trainingItem2') }}</li>
            <li>{{ t('help.trainingItem3') }}</li>
          </ul>
        </section>
        <aside class="cloud-card help-accent">
          <h3 class="section-title">{{ t('help.editorTitle') }}</h3>
          <p class="help-text">{{ t('help.editorDesc') }}</p>
          <h3 class="section-title">{{ t('help.shortcutsTitle') }}</h3>
          <ul class="help-list help-shortcuts">
            <li><kbd>← ↑ ↓ →</kbd> {{ t('help.shortcutOrbit') }}</li>
            <li><kbd>+</kbd> / <kbd>-</kbd> {{ t('help.shortcutZoom') }}</li>
            <li><kbd>WASD</kbd> {{ t('help.shortcutFly') }}</li>
            <li><kbd>Esc</kbd> {{ t('help.shortcutView') }}</li>
          </ul>
        </aside>
      </div>
    </div>
  </div>
</template>
