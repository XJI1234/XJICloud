import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import i18n, { applyDocumentLang, resolveStoredLocale, toI18nLocale, type AppLocaleCode } from '@/i18n'

const LOCALE_STORAGE_KEY = 'xjicloud_locale'

export function useAppLocale() {
  const { locale } = useI18n()

  const currentLocale = computed<AppLocaleCode>(() => (locale.value === 'en-US' ? 'en' : 'zh'))

  function setLocale(code: AppLocaleCode) {
    locale.value = toI18nLocale(code)
    localStorage.setItem(LOCALE_STORAGE_KEY, code)
    applyDocumentLang(code)
  }

  return {
    currentLocale,
    setLocale,
  }
}

export function useFormatDateTime() {
  const { locale } = useI18n()

  function formatDateTime(value: string | number | Date) {
    const date = value instanceof Date ? value : new Date(value)
    return new Intl.DateTimeFormat(locale.value, {
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date)
  }

  return { formatDateTime }
}

export function getI18nLocale(): AppLocaleCode {
  return i18n.global.locale.value === 'en-US' ? 'en' : 'zh'
}
