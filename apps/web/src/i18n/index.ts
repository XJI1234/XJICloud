import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocaleCode = 'zh' | 'en'

const LOCALE_STORAGE_KEY = 'xjicloud_locale'

export function resolveStoredLocale(): AppLocaleCode {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  return stored === 'en' ? 'en' : 'zh'
}

export function toI18nLocale(code: AppLocaleCode): 'zh-CN' | 'en-US' {
  return code === 'en' ? 'en-US' : 'zh-CN'
}

export function applyDocumentLang(code: AppLocaleCode) {
  document.documentElement.lang = code === 'en' ? 'en' : 'zh-CN'
}

const initialLocale = resolveStoredLocale()
applyDocumentLang(initialLocale)

const i18n = createI18n({
  legacy: false,
  locale: toI18nLocale(initialLocale),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export default i18n
