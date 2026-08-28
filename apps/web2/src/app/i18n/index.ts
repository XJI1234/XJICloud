import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocaleCode = 'zh' | 'en'

const LOCALE_STORAGE_KEY = 'xjicloud_locale'

const errorMessages = {
  'zh-CN': {
    errors: {
      AUTH_CREDENTIALS_REQUIRED: '请填写用户名和密码',
      AUTH_CAPTCHA_REQUIRED: '请输入验证码',
      AUTH_CAPTCHA_LOAD_FAILED: '验证码加载失败',
      AUTH_UNAUTHORIZED: '登录已失效，请重新登录',
      DATASET_NO_IMAGES: '所选文件夹中没有 JPG / PNG / WebP 图片',
      DATASET_MISSING_UPLOAD_URL: '缺少 {name} 的上传地址',
      JOB_CANNOT_CANCEL: '当前任务无法取消',
      JOB_CANNOT_DELETE: '当前任务无法删除',
      PROJECT_NAME_REQUIRED: '请输入项目名称',
      PROJECT_NOT_FOUND: '项目不存在',
      MODEL_INVALID_FORMAT: '仅支持 PLY / SPZ 模型',
      MODEL_PROJECT_REQUIRED: '请先打开一个项目',
      VIEWER_CONFIG_INVALID: '视图配置无效',
      EDITOR_NOT_READY: '编辑器尚未就绪',
      EDITOR_TIMEOUT: '编辑器响应超时',
      EDITOR_EXPORT_FAILED: '导出失败',
      EDITOR_IMPORT_FAILED: '本地模型打开失败',
      OSS_UPLOAD_FAILED: '对象存储上传失败',
      SSE_CONNECT_FAILED: '任务进度连接失败',
      NETWORK: '网络异常',
      UNKNOWN: '操作失败',
    },
  },
  'en-US': {
    errors: {
      AUTH_CREDENTIALS_REQUIRED: 'Enter username and password',
      AUTH_CAPTCHA_REQUIRED: 'Enter the captcha',
      AUTH_CAPTCHA_LOAD_FAILED: 'Captcha failed to load',
      AUTH_UNAUTHORIZED: 'Session expired. Sign in again.',
      DATASET_NO_IMAGES: 'No JPG / PNG / WebP images in the selected folder',
      DATASET_MISSING_UPLOAD_URL: 'Missing upload URL for {name}',
      JOB_CANNOT_CANCEL: 'This job cannot be cancelled',
      JOB_CANNOT_DELETE: 'This job cannot be deleted',
      PROJECT_NAME_REQUIRED: 'Enter a project name',
      PROJECT_NOT_FOUND: 'Project not found',
      MODEL_INVALID_FORMAT: 'Only PLY / SPZ models are supported',
      MODEL_PROJECT_REQUIRED: 'Open a project first',
      VIEWER_CONFIG_INVALID: 'Viewer config is invalid',
      EDITOR_NOT_READY: 'Editor is not ready',
      EDITOR_TIMEOUT: 'Editor timed out',
      EDITOR_EXPORT_FAILED: 'Export failed',
      EDITOR_IMPORT_FAILED: 'Failed to open the local model',
      OSS_UPLOAD_FAILED: 'Object storage upload failed',
      SSE_CONNECT_FAILED: 'Job progress connection failed',
      NETWORK: 'Network error',
      UNKNOWN: 'Something went wrong',
    },
  },
} as const

export function resolveStoredLocale(): AppLocaleCode {
  if (typeof localStorage === 'undefined') {
    return 'zh'
  }
  return localStorage.getItem(LOCALE_STORAGE_KEY) === 'en' ? 'en' : 'zh'
}

export function toI18nLocale(code: AppLocaleCode): 'zh-CN' | 'en-US' {
  return code === 'en' ? 'en-US' : 'zh-CN'
}

export function applyDocumentLang(code: AppLocaleCode) {
  document.documentElement.lang = code === 'en' ? 'en' : 'zh-CN'
}

const initialLocale = resolveStoredLocale()
if (typeof document !== 'undefined') {
  applyDocumentLang(initialLocale)
}

const i18n = createI18n({
  legacy: false,
  locale: toI18nLocale(initialLocale),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': { ...zhCN, ...errorMessages['zh-CN'] },
    'en-US': { ...enUS, ...errorMessages['en-US'] },
  },
})

export default i18n
