import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocaleCode = 'zh' | 'en'

const LOCALE_STORAGE_KEY = 'xjicloud_locale'

const errorMessages = {
  'zh-CN': {
    errors: {
      AUTH_SMS_UNAVAILABLE: '短信注册尚未开通或暂不可用，请稍后重试或联系管理员',
      AUTH_PHONE_INVALID: "请输入中国大陆11位手机号",
      AUTH_PASSWORD_INVALID: "密码须为8至64个字符，UTF-8不超过72字节",
      AUTH_SMS_REQUIRED: "请先获取并填写6位短信验证码",
      AUTH_PROFILE_INVALID: "请填写昵称、有效邮箱和单位，并检查字段长度",
      AUTH_CREDENTIALS_REQUIRED: '请填写用户名和密码',
      AUTH_CAPTCHA_REQUIRED: '请输入验证码',
      AUTH_CAPTCHA_LOAD_FAILED: '验证码加载失败',
      AUTH_UNAUTHORIZED: '登录已失效，请重新登录',
      DATASET_NO_IMAGES: '所选文件夹中没有 JPG / PNG / WebP 图片',
      DATASET_MISSING_UPLOAD_URL: '上传准备失败，请重试',
      JOB_CANNOT_CANCEL: '当前任务无法取消',
      JOB_CANNOT_DELETE: '当前任务无法删除',
      PROJECT_NAME_REQUIRED: '请输入项目名称',
      PROJECT_NOT_FOUND: '项目不存在',
      MODEL_INVALID_FORMAT: '仅支持 PLY / SPZ 模型',
      MODEL_TOO_LARGE: '文件不能超过 2GB',
      MODEL_DELETE_FAILED: '删除模型失败',
      MODEL_PROJECT_REQUIRED: '请先打开一个项目',
      VIEWER_CONFIG_INVALID: '视图配置无效',
      VIEWER_WEBGPU_UNAVAILABLE: '查看器需要支持 WebGPU 与本地存储的浏览器。请使用开启硬件加速的新版 Edge 或 Chrome，并通过 HTTPS 或 localhost 访问。',
      VIEWER_UNSUPPORTED_FORMAT: '模型格式不受支持。请使用二进制 PLY 或 SPZ，暂不支持 ASCII PLY。',
      VIEWER_RESOURCE_LIMIT: '此模型超出浏览器或图形设备的资源限制，请尝试较小的模型。',
      VIEWER_LOAD_FAILED: '模型未能加载，请检查文件以及查看器资源是否完整。',
      VIEWER_DEVICE_LOST: '图形设备暂时不可用，请尝试恢复或重新打开页面。',
      VIEWER_SCREENSHOT_FAILED: '截图保存失败，请稍后重试。',
      VIEWER_CLEANUP_FAILED: '场景已关闭，但浏览器临时存储未完全清理。请重新打开页面后重试。',
      VIEWER_CANCELLED: '已取消加载',
      VIEWER_TIMEOUT: '模型准备超时，请检查网络或尝试较小的模型。',
      VIEWER_ACTION_FAILED: '查看操作未完成，请重试。',
      EDITOR_NOT_READY: '编辑器尚未就绪',
      EDITOR_TIMEOUT: '编辑器响应超时',
      EDITOR_EXPORT_FAILED: '导出失败',
      EDITOR_IMPORT_FAILED: '本地模型打开失败',
      OSS_UPLOAD_FAILED: '上传失败，请重试',
      SSE_CONNECT_FAILED: '暂时无法获取训练进度',
      NETWORK: '上传失败，请检查网络后重试',
      OPERATION_CANCELLED: '操作已取消',
      UNKNOWN: '操作失败',
    },
  },
  'en-US': {
    errors: {
      AUTH_SMS_UNAVAILABLE: 'SMS registration is not enabled or is temporarily unavailable. Retry later or contact the administrator.',
      AUTH_PHONE_INVALID: "Enter an 11-digit mainland China phone number",
      AUTH_PASSWORD_INVALID: "Use 8–64 characters and at most 72 UTF-8 bytes",
      AUTH_SMS_REQUIRED: "Request an SMS code and enter its 6 digits",
      AUTH_PROFILE_INVALID: "Enter a nickname, valid email and organization within the length limits",
      AUTH_CREDENTIALS_REQUIRED: 'Enter username and password',
      AUTH_CAPTCHA_REQUIRED: 'Enter the captcha',
      AUTH_CAPTCHA_LOAD_FAILED: 'Captcha failed to load',
      AUTH_UNAUTHORIZED: 'Session expired. Sign in again.',
      DATASET_NO_IMAGES: 'No JPG / PNG / WebP images in the selected folder',
      DATASET_MISSING_UPLOAD_URL: 'Could not prepare the upload. Please try again.',
      JOB_CANNOT_CANCEL: 'This job cannot be cancelled',
      JOB_CANNOT_DELETE: 'This job cannot be deleted',
      PROJECT_NAME_REQUIRED: 'Enter a project name',
      PROJECT_NOT_FOUND: 'Project not found',
      MODEL_INVALID_FORMAT: 'Only PLY / SPZ models are supported',
      MODEL_TOO_LARGE: 'Files cannot exceed 2GB',
      MODEL_DELETE_FAILED: 'Failed to delete the model',
      MODEL_PROJECT_REQUIRED: 'Open a project first',
      VIEWER_CONFIG_INVALID: 'Viewer config is invalid',
      VIEWER_WEBGPU_UNAVAILABLE: 'This viewer needs WebGPU and browser storage. Use a recent Edge or Chrome with hardware acceleration over HTTPS or localhost.',
      VIEWER_UNSUPPORTED_FORMAT: 'Unsupported model format. Use binary PLY or SPZ; ASCII PLY is not supported.',
      VIEWER_RESOURCE_LIMIT: 'This model exceeds browser or graphics-device limits. Try a smaller model.',
      VIEWER_LOAD_FAILED: 'The model could not be loaded. Check the file and the viewer assets.',
      VIEWER_DEVICE_LOST: 'The graphics device is unavailable. Try recovery or reopen this page.',
      VIEWER_SCREENSHOT_FAILED: 'Could not save the screenshot. Please try again.',
      VIEWER_CLEANUP_FAILED: 'The scene closed, but temporary browser storage could not be fully cleared. Reopen this page and try again.',
      VIEWER_CANCELLED: 'Loading cancelled',
      VIEWER_TIMEOUT: 'Model preparation timed out. Check your connection or try a smaller model.',
      VIEWER_ACTION_FAILED: 'The viewing action could not be completed. Please try again.',
      EDITOR_NOT_READY: 'Editor is not ready',
      EDITOR_TIMEOUT: 'Editor timed out',
      EDITOR_EXPORT_FAILED: 'Export failed',
      EDITOR_IMPORT_FAILED: 'Failed to open the local model',
      OSS_UPLOAD_FAILED: 'Upload failed. Please try again.',
      SSE_CONNECT_FAILED: 'Training progress is temporarily unavailable',
      NETWORK: 'Upload failed. Check your network and try again.',
      OPERATION_CANCELLED: 'Operation cancelled',
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
