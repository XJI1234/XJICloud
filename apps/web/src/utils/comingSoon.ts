import i18n from '@/i18n'

export function showComingSoon(featureKey: string) {
  const feature = i18n.global.t(featureKey)
  window.alert(i18n.global.t('common.comingSoon', { feature }))
}
