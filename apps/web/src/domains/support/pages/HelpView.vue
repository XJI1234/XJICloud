<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="help-page">
    <div class="cloud-page-inner">
      <p class="eyebrow">{{ t('help.eyebrow') }}</p>
      <h2 class="help-title">{{ t('help.title') }}</h2>
      <p class="help-subtitle">{{ t('help.subtitle') }}</p>

      <div class="help-grid">
        <section class="cloud-card help-card">
          <h3 class="section-title">{{ t('help.gettingStarted') }}</h3>
          <ol class="help-list">
            <li>{{ t('help.stepRegister') }}</li>
            <li>{{ t('help.stepProject') }}</li>
            <li>{{ t('help.stepUpload') }}</li>
            <li>{{ t('help.stepView') }}</li>
          </ol>
        </section>

        <section class="cloud-card help-card">
          <h3 class="section-title">{{ t('help.trainingTitle') }}</h3>
          <p class="help-text">{{ t('help.trainingDesc') }}</p>
          <ul class="help-list">
            <li>{{ t('help.trainingItem1') }}</li>
            <li>{{ t('help.trainingItem2') }}</li>
            <li>{{ t('help.trainingItem3') }}</li>
          </ul>
        </section>

        <section class="cloud-card help-card">
          <h3 class="section-title">{{ t('help.editorTitle') }}</h3>
          <p class="help-text">{{ t('help.editorDesc') }}</p>
        </section>

        <section class="cloud-card help-card">
          <h3 class="section-title">{{ t('help.shortcutsTitle') }}</h3>
          <ul class="help-list help-shortcuts">
            <li><kbd>O</kbd> {{ t('help.shortcutOpen') }}</li>
            <li><kbd>1</kbd> / <kbd>2</kbd> / <kbd>3</kbd> {{ t('help.shortcutTools') }}</li>
            <li><kbd>Esc</kbd> {{ t('help.shortcutView') }}</li>
            <li><kbd>Ctrl+Z</kbd> {{ t('help.shortcutUndo') }}</li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>
