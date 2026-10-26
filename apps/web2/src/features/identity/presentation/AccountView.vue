<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { useAuthSession } from '@/features/identity/presentation/composables/useAuthSession'
import type { AccountProfile } from '@/features/identity/domain/entities/account-profile.entity'

const auth = useAuthSession()
const router = useRouter()
const { t } = useI18n()
const profile = ref<AccountProfile | null>(null)
const displayName = ref('')
const email = ref('')
const organization = ref('')
const pending = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const saved = ref(false)

async function load() {
  loading.value = true
  errorMessage.value = ''
  const [error, value] = await auth.loadProfile()
  if (error) errorMessage.value = formatDomainError(t, error)
  else if (value) {
    profile.value = value
    displayName.value = value.profileRequired ? '' : value.displayName
    email.value = value.email
    organization.value = value.organization
  }
  loading.value = false
}

async function save() {
  if (pending.value) return
  pending.value = true
  errorMessage.value = ''
  saved.value = false
  const wasRequired = profile.value?.profileRequired
  try {
    const [error, value] = await auth.updateProfile({
      displayName: displayName.value, email: email.value, organization: organization.value,
    })
    if (error) errorMessage.value = formatDomainError(t, error)
    else if (value) {
      profile.value = value
      saved.value = true
      if (wasRequired) await router.replace({ name: 'home' })
    }
  } finally {
    pending.value = false
  }
}
onMounted(load)
</script>

<template>
  <section class="account-page">
    <header>
      <p class="account-eyebrow">{{ t('account.eyebrow') }}</p>
      <h1>{{ t('account.title') }}</h1>
      <p class="account-lead">{{ profile?.profileRequired ? t('account.completeHint') : t('account.editHint') }}</p>
    </header>
    <p v-if="loading" role="status">{{ t('common.processing') }}</p>
    <form v-else-if="profile" class="account-card" @submit.prevent="save">
      <div class="account-identity">
        <span>{{ profile.phone ? t('registration.phone') : t('login.username') }}</span>
        <strong>{{ profile.phone || profile.username }}</strong>
        <small>{{ profile.phone ? t('account.phoneVerified') : t('account.legacyAccount') }}</small>
      </div>
      <label class="cloud-field">
        <span>{{ t('account.nickname') }}</span>
        <input v-model="displayName" class="cloud-input" name="displayName" autocomplete="nickname"
          maxlength="128" required :disabled="pending" />
      </label>
      <label class="cloud-field">
        <span>{{ t('account.email') }}</span>
        <input v-model="email" class="cloud-input" name="email" type="email" autocomplete="email"
          maxlength="254" required :disabled="pending" />
        <small>{{ t('account.emailHint') }}</small>
      </label>
      <label class="cloud-field">
        <span>{{ t('account.organization') }}</span>
        <input v-model="organization" class="cloud-input" name="organization" autocomplete="organization"
          maxlength="200" required :disabled="pending" :placeholder="t('account.organizationHint')" />
      </label>
      <p v-if="saved" class="account-success" role="status">{{ t('account.saved') }}</p>
      <AppButton variant="primary" type="submit" :disabled="pending">
        {{ pending ? t('common.processing') : profile.profileRequired ? t('account.saveAndContinue') : t('account.save') }}
      </AppButton>
    </form>
    <p v-if="errorMessage" class="login-error" role="alert">{{ errorMessage }}</p>
    <AppButton v-if="!loading && !profile" @click="load">{{ t('account.retry') }}</AppButton>
  </section>
</template>

<style scoped>
.account-page { width: min(100%, 720px); margin: 0 auto; padding: 40px 28px 64px; box-sizing: border-box; }
.account-eyebrow { margin: 0 0 12px; font-size: 12px; letter-spacing: .12em; color: #3d6b8a; }
h1 { margin: 0; font-size: 28px; font-weight: 600; }
.account-lead { color: #647078; line-height: 1.7; margin: 12px 0 28px; }
.account-card { padding: 28px; border: 1px solid #e4e8e9; border-radius: 16px; background: #fff; display: grid; gap: 22px; }
.account-identity { display: grid; gap: 6px; padding-bottom: 20px; border-bottom: 1px solid #e4e8e9; }
.account-identity span, small { font-size: 12px; color: #647078; line-height: 1.6; }
.account-identity strong { font-size: 18px; font-weight: 500; }
.account-success { color: #35734b; margin: 0; }
@media (max-width: 600px) { .account-page { padding: 24px 16px; } .account-card { padding: 20px; } }
</style>
