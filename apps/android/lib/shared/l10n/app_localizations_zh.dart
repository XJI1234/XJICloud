// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => '玄境云';

  @override
  String get home => '主页';

  @override
  String get projects => '工程管理';

  @override
  String get viewer => '模型查看';

  @override
  String get account => '我的';

  @override
  String get upload => '上传';

  @override
  String get uploadImages => '上传图片并训练';

  @override
  String get uploadModel => '上传 PLY/SPZ 模型';

  @override
  String get loading => '处理中';

  @override
  String get cancel => '取消';

  @override
  String get confirm => '确认';

  @override
  String get welcome => '欢迎使用玄境云';

  @override
  String get login => '登录';

  @override
  String get register => '注册';

  @override
  String get username => '用户名';

  @override
  String get password => '密码';

  @override
  String get confirmPassword => '确认密码';

  @override
  String get displayName => '显示名称（可选）';

  @override
  String get captchaCode => '验证码';

  @override
  String get refreshCaptcha => '刷新验证码';

  @override
  String get showPassword => '显示密码';

  @override
  String get hidePassword => '隐藏密码';

  @override
  String get switchRegister => '没有账号？注册';

  @override
  String get switchLogin => '已有账号？登录';

  @override
  String get registrationSuccess => '注册成功，请登录';

  @override
  String get language => '语言';

  @override
  String get toggleLanguage => 'English';

  @override
  String get help => '帮助与关于';

  @override
  String get helpContent =>
      'XJICloud 用于 3D 高斯模型管理与查看。账号信息来自本次登录会话。需要连接服务器才能登录或注册。会话过期后请重新登录。';

  @override
  String get logout => '退出登录';

  @override
  String get sessionExpiry => '会话到期时间';

  @override
  String get userId => '账号 ID';

  @override
  String get retry => '重试';

  @override
  String get close => '关闭';

  @override
  String get requiredField => '请填写此项';

  @override
  String get usernameValidation => '用户名需要 3–64 个字符';

  @override
  String get passwordValidation => '密码需要 6–128 个字符';

  @override
  String get confirmationValidation => '两次输入的密码不一致';

  @override
  String get displayNameValidation => '显示名称最多 128 个字符';

  @override
  String get errorNetwork => '网络连接失败，请重试';

  @override
  String get errorCredentials => '用户名或密码错误';

  @override
  String get errorCaptcha => '验证码错误或已失效，请输入新验证码';

  @override
  String get errorCaptchaRequired => '请输入图片验证码';

  @override
  String get errorUsernameTaken => '用户名已存在';

  @override
  String get errorRateLimit => '请求过于频繁，请稍后重试';

  @override
  String get errorStorage => '无法安全读取或清除会话。请重试；如仍失败，请在系统设置中清除应用数据。';

  @override
  String get errorExpired => '会话已到期，请重新登录';

  @override
  String get errorRequest => '操作失败，请重试';

  @override
  String get errorForbidden => '服务器拒绝访问，请重新登录或联系管理员';

  @override
  String get errorProtocol => '服务器响应异常，请稍后重试';

  @override
  String get errorPreferences => '语言设置未能保存，请重试';

  @override
  String get configurationError => '服务地址配置无效';

  @override
  String get viewLicenses => '开源许可证';

  @override
  String get sdkAbout => 'Native3DGS SDK 0.2.2 · XJICloud 安卓客户端';

  @override
  String get currentProject => '当前工程';

  @override
  String get recentProjects => '最近打开';

  @override
  String get createProject => '创建工程';

  @override
  String get firstProject => '创建第一个工程';

  @override
  String get projectName => '工程名称';

  @override
  String get projectDescription => '描述';

  @override
  String get saveProject => '保存';

  @override
  String get editProject => '编辑工程';

  @override
  String get deleteProject => '删除工程';

  @override
  String get setCurrentProject => '设为当前工程';

  @override
  String get projectCreated => '创建时间';

  @override
  String get projectEmpty => '暂无工程';

  @override
  String get selectProject => '选择工程以查看详情。';

  @override
  String get projectUnavailable => '该工程不可用，请刷新工程列表。';

  @override
  String get projectValidation => '请输入 1–256 字的工程名称，描述最多 2000 字。';

  @override
  String get workspaceStorage => '无法保存或读取工作区，请重试。';

  @override
  String get projectDeletedStorage => '工程已删除，但本地工作区保存失败；请刷新以同步。';

  @override
  String get deleteProjectMessage =>
      '删除此工程及其全部模型、训练任务和数据集？此操作无法撤销，服务端正在运行的任务可能继续。 本地活动传输会先停止。';

  @override
  String get discardChanges => '放弃修改？';

  @override
  String get discardChangesMessage => '当前修改尚未保存。';

  @override
  String get keepEditing => '继续编辑';

  @override
  String get discard => '放弃修改';

  @override
  String get projectsIntro => '用工程整理模型与训练素材。';

  @override
  String get noRecentProjects => '打开过的工程将显示在这里。';

  @override
  String get noCurrentProject => '选择工程以继续。';

  @override
  String get openProject => '打开工程';

  @override
  String get models => '模型资产';

  @override
  String get modelEmpty => '此工程暂无模型';

  @override
  String get modelUploadPolicy =>
      '支持 PLY/SPZ，文件大小 1 字节至 2 GiB。选择后将创建私有副本用于安全续传。';

  @override
  String get modelValidation => '请选择有效的 PLY/SPZ 文件（1 字节至 2 GiB）。空间不足时请先释放设备存储。';

  @override
  String get modelDownload => '下载到私有缓存';

  @override
  String get modelCached => '已缓存';

  @override
  String get cacheRemove => '删除此缓存副本';

  @override
  String get modelCreated => '创建时间';

  @override
  String get modelUpdated => '更新时间';

  @override
  String get deleteModel => '删除云模型';

  @override
  String get deleteModelMessage => '删除此云模型并失效本账号缓存？此操作无法撤销。';

  @override
  String get pauseTransfer => '暂停传输';

  @override
  String get resumeTransfer => '核对断点并续传';

  @override
  String get reselectSource => '重选相同源文件';

  @override
  String get abandonUpload => '取消此上传';

  @override
  String get abandonUploadMessage => '停止本地跟踪并取消上传会话。若服务器已完成模型，云模型会保留，请在列表中核对。';

  @override
  String get uploadReady => '准备上传';

  @override
  String get uploadPaused => '已暂停，可核对断点后续传';

  @override
  String get uploadCompleting => '正在确认完成';

  @override
  String get uploadUncertain => '完成结果待核对';

  @override
  String get uploadCompleted => '服务器已确认完成，请核对最新模型列表';

  @override
  String get uploadPreparing => '正在读取文件并准备私有副本';

  @override
  String get completionUncertain =>
      '服务器完成结果未确认。列表已刷新，请按模型标识与创建时间核对。不会自动重复上传或选择同名模型。';

  @override
  String get uploadCreateUncertain =>
      '创建上传会话的响应未确认，未自动重试。请核对工程模型；未完成会话会由服务器过期清理。';

  @override
  String get retryCompletion => '再次请求完成';

  @override
  String get retryCompletionMessage =>
      '请先核对模型列表。当前服务器重复完成可能返回“模型不存在”；仅在你确认后再次请求。不会新建上传。';

  @override
  String get reconcileModel => '确认为此次上传结果';

  @override
  String get reconcileModelMessage => '请核对模型标识、文件名、大小和创建时间，确认这是此次上传生成的资产。';

  @override
  String get sourceChanged => '源文件丢失或内容已改变。请重选完全相同的文件；不同内容不能续传。';

  @override
  String get cacheTitle => '模型缓存';

  @override
  String get cacheDescription => '仅管理当前账号的模型下载。正在使用的文件会保留，源文件与上传副本不会被清除。';

  @override
  String get cacheClear => '清除可释放缓存';

  @override
  String get cacheFreed => '已释放字节';

  @override
  String get cacheBytes => '缓存字节';

  @override
  String get cacheFull => '缓存配额不足，或所有可释放文件正在使用。请先清理缓存或关闭模型。';

  @override
  String get cacheInUse => '此缓存正在使用，请关闭模型后重试。';

  @override
  String get modelNotFound => '模型或上传会话不存在，可能已删除或过期。请刷新工程列表后核对。';

  @override
  String get modelDeletedStorage => '云模型已删除，但本地缓存清理失败，请重试清理缓存。';

  @override
  String get uploadCompletedStorage => '服务器已确认完成，但本地记录保存失败。请核对列表后结束本地跟踪。';

  @override
  String get cancelledTransfer => '传输已暂停或取消，未提交不完整缓存。';

  @override
  String get finishUpload => '结束本地跟踪';

  @override
  String get uploadDestination => '固定目标工程';

  @override
  String get cancelDownload => '取消下载';

  @override
  String get modelUnavailable => '所选模型不可用，请重新刷新并核对。';

  @override
  String get sourceBusy => '系统文件选择器仍在使用，请先完成或取消上一次选择。';

  @override
  String get chooseImages => '选择多张图片';

  @override
  String get chooseImageFolder => '选择图片文件夹';

  @override
  String get datasetPolicy =>
      '选择 1–9999 张 JPG、PNG 或 WebP 图片。原图逐张上传，不压缩、不转码；切到后台会暂停发送。';

  @override
  String get datasetName => '训练任务名称';

  @override
  String get datasetPreview => '归档预览';

  @override
  String get datasetImageCount => '图片数量';

  @override
  String get datasetTotalBytes => '上传总字节';

  @override
  String get datasetPaused => '本地上传已暂停；服务端状态仍以任务快照为准。';

  @override
  String get datasetResume => '查询任务并继续';

  @override
  String get datasetRestart => '取消旧任务并重新提交';

  @override
  String get datasetRestartMessage =>
      '签名无法补签。将先查询并取消仍在上传的旧任务，再用这些素材创建新任务并全部重传。若源文件已丢失，请重新选图。';

  @override
  String get datasetRestartRequired =>
      '重启后签名不可恢复，或签名已过期。先查询任务；仍在上传时，确认取消旧任务再重新提交。';

  @override
  String get datasetCreateUncertain =>
      '创建响应不确定，未自动重试。请刷新任务列表，核对任务 ID、名称和创建时间，再明确选择此次任务。无法自动推断身份。';

  @override
  String get datasetValidation =>
      '请选择 1–9999 张非空 JPG、PNG 或 WebP 图片；文件名、格式和 MIME 必须一致。不支持 HEIC。';

  @override
  String get datasetDiscard => '取消上传并清除素材';

  @override
  String get datasetDiscardMessage =>
      '停止本地发送，并查询和取消此次服务端任务。未提交素材将被清除。创建结果不确定时须先核对任务身份。';

  @override
  String get datasetLeave => '暂停并离开';

  @override
  String get datasetLeaveMessage => '素材与已确认上传项会保留。后台不会继续发送；返回后先查询任务再恢复。';

  @override
  String get datasetConfirmCandidate => '确认为此次创建的任务';

  @override
  String get datasetCandidateMessage =>
      '请核对任务 ID、名称和创建时间。此操作仅关联记录；不会推断上传对象或恢复丢失的签名。';

  @override
  String get datasetNoCandidate => '已核对列表，未找到此次任务';

  @override
  String get datasetNoCandidateMessage =>
      '仅在已核对完整任务列表并确认此次创建未成功时继续。将清除此创建意图并保留素材，随后可手动提交新任务。';

  @override
  String get datasetCompleteRetry => '核对后再次请求入队';

  @override
  String get datasetCompleteMessage =>
      '完成请求并非幂等。仅当所有图片和 manifest 已确认上传、最新查询仍为 UPLOADING 时再次请求；客户端不会自动重试。';

  @override
  String get trainingJobs => '训练任务';

  @override
  String get noTrainingJobs => '此工程还没有训练任务';

  @override
  String get jobId => '任务 ID';

  @override
  String get jobCancel => '取消任务';

  @override
  String get jobCancelMessage => '取消不能保证立即中断 Worker，任务仍可能随后完成。页面会继续查询真实状态。';

  @override
  String get jobDelete => '删除任务记录';

  @override
  String get jobDeleteMessage => '删除此终态任务记录及本地下载副本？';

  @override
  String get jobStateError => '任务状态已变化，请查询最新任务。';

  @override
  String get outputDownload => '下载训练产物';

  @override
  String get outputDownloaded => '训练产物已下载到私有存储';

  @override
  String get outputUnavailable => '服务端尚未提供训练产物下载地址';

  @override
  String get outputUrlExpired =>
      '产物下载签名已失效。当前服务端可能重复返回旧地址，重新查询不能保证补签；请联系服务端维护者。';

  @override
  String get jobPending => '待处理';

  @override
  String get jobUploading => '上传中';

  @override
  String get jobQueued => '排队中';

  @override
  String get jobRunning => '训练中';

  @override
  String get jobCompleted => '已完成';

  @override
  String get jobFailed => '失败';

  @override
  String get jobCancelled => '已取消';

  @override
  String get viewerOpenLocal => '打开本机 PLY/SPZ';

  @override
  String get viewerChooseCloud => '浏览云端模型';

  @override
  String get viewerOpen => '查看模型';

  @override
  String get viewerTools => '查看工具';

  @override
  String get viewerFixed => '固定模式';

  @override
  String get viewerFree => '自由模式';

  @override
  String get viewerFit => '适配';

  @override
  String get viewerReset => '重置';

  @override
  String get viewerFlip => 'Y 翻转';

  @override
  String get viewerZoomIn => '放大';

  @override
  String get viewerZoomOut => '缩小';

  @override
  String get viewerFullscreen => '全屏';

  @override
  String get viewerExitFullscreen => '退出全屏';

  @override
  String get viewerClose => '关闭模型';

  @override
  String get viewerCancel => '取消加载';

  @override
  String get viewerEmpty => '打开本机文件，或从当前工程选择一个模型。';

  @override
  String get viewerDownload => '下载 / 准备本机文件';

  @override
  String get viewerDecode => '解码';

  @override
  String get viewerUpload => 'GPU 上传 / 等待首帧';

  @override
  String get viewerFirstFrame => '首帧就绪';

  @override
  String get viewerRecover => '恢复画布';

  @override
  String get viewerClosing => '正在关闭模型';

  @override
  String get viewerError => '模型加载或渲染失败。可取消或重新选择有效 PLY/SPZ；已有场景会保留。';

  @override
  String get viewerUnsupported =>
      '当前平台不支持原生查看器。需要 Android 10+、arm64、Vulkan 1.1 和 4KB 内存页。';

  @override
  String get viewerConfigFit => '缺少兼容视角元数据，已自动取景；不会覆盖云端配置。';

  @override
  String get viewerForward => '前进';

  @override
  String get viewerBackward => '后退';

  @override
  String get viewerLeft => '左移';

  @override
  String get viewerRight => '右移';

  @override
  String get viewerUp => '上移';

  @override
  String get viewerDown => '下移';

  @override
  String get viewerOutput => '打开训练产物';

  @override
  String get viewerOutputNote => '训练产物须为有效 Gaussian PLY；任务完成不代表格式兼容。';

  @override
  String get viewerCloudTitle => '云端模型';

  @override
  String get viewerLocalTitle => '本机模型';

  @override
  String get viewerRefresh => '刷新模型';

  @override
  String get viewerProject => '所属工程';

  @override
  String get fusionLogin => '本机号码一键登录 / 注册';

  @override
  String get passwordLogin => '密码登录';

  @override
  String get phoneRegistration => '手机号注册';

  @override
  String get privacyConsent => '我同意号码认证，并已阅读';

  @override
  String get fusionPrivacyPurpose =>
      '使用本机号码登录或注册。经您同意后，将使用手机号、网络和设备信息完成认证，并请您确认运营商协议。';

  @override
  String get fusionPrivacyPolicy => '隐私权政策';

  @override
  String get privacyRequired => '请先阅读数据处理说明并勾选同意，再使用一键认证。';

  @override
  String get fusionUnavailable => '当前设备或服务暂不支持一键认证，可继续手机号注册或密码登录。';

  @override
  String get fusionFailed => '号码认证未完成，可继续手机号注册或稍后重试。';

  @override
  String get fusionBackendFailed => '认证服务暂未完成登录，可使用手机号注册或稍后重试。';

  @override
  String get phoneNumber => '大陆手机号';

  @override
  String get phoneValidation => '请输入11位大陆手机号。';

  @override
  String get sendSms => '发送短信验证码';

  @override
  String get resendSms => '重新发送短信验证码';

  @override
  String get smsCode => '6位短信验证码';

  @override
  String get smsValidation => '请输入6位短信验证码。';

  @override
  String get smsRequired => '请先获取短信验证码验证手机号。';

  @override
  String get registrationPasswordValidation => '密码为8–64个字符，UTF-8编码不超过72字节。';

  @override
  String get registrationValidation => '请检查手机号、短信验证码、密码及确认密码。';

  @override
  String get completeProfileTitle => '完善账户资料';

  @override
  String get profileIntro => '补全昵称、邮箱和单位后即可使用云服务。';

  @override
  String get profileEmail => '邮箱';

  @override
  String get profileOrganization => '单位';

  @override
  String get saveProfile => '保存资料';

  @override
  String get editProfile => '账户资料';

  @override
  String get profileValidation => '请填写有效昵称、邮箱和单位。';

  @override
  String get profileRequiredError => '请先完善账户资料。';

  @override
  String get phoneRegistered => '该手机号已注册，请使用密码登录。';

  @override
  String get phoneUnavailable => '未识别到本机号码';

  @override
  String get policyUnavailable => '暂时无法打开隐私权政策，请稍后重试。';

  @override
  String get privacyEffectiveDate => '更新日期：2026年10月27日\n生效日期：2026年10月27日';

  @override
  String get privacyIntroduction =>
      '玄境云（XJICloud）由东南大学玄境创新团队运营。本政策说明我们如何在账号认证及云建模服务中处理您的个人信息，以及您可以如何管理信息。请在使用相关功能前阅读本政策，尤其是手机号、上传文件和第三方认证服务的说明。\n\n运营主体：东南大学玄境创新团队\n隐私联系邮箱：baokx@seu.edu.cn';

  @override
  String get privacyCollectionTitle => '一、我们如何收集和使用信息';

  @override
  String get privacyCollection =>
      '账号注册与登录：您通过短信注册时，我们处理您输入的手机号、图形验证码、短信验证码和设置的密码，以核验手机号、创建账号和保障登录安全。密码在服务端以不可逆散列形式保存。通过本机号码认证时，我们仅以认证服务核验通过的手机号登录或创建同一账号。\n\n资料完善：您提供的昵称、邮箱和单位用于账户资料展示与管理；填写邮箱不代表已经验证邮箱所有权。\n\n云建模与模型查看：只有在您主动选择、上传或打开文件时，应用才会处理对应的照片、图片数据集、PLY/SPZ模型及相关文件名称、大小和任务信息，用于上传、训练、查看和下载。请勿上传未经授权的他人个人信息。使用系统文件选择器不代表授权访问全部文件。\n\n安全与会话：为维持登录和控制访问，设备会安全保存登录凭证及必要账号信息；服务端处理请求IP、认证状态和必要的操作记录，用于限流、识别异常、处理故障及防止重复使用认证凭据。我们不会将验证码或认证凭据用于广告营销。';

  @override
  String get privacyChoiceTitle => '二、您的选择与认证权限';

  @override
  String get privacyChoice =>
      '本机号码一键登录/注册是可选功能。仅当您主动勾选同意本政策并点击一键登录/注册后，我们才检查SIM可用状态并启动后续号码认证；未勾选时不初始化融合认证SDK。运营商授权页还会单独展示相关协议，由您自主决定是否接受。仅阅读政策不会代您同意。\n\n未识别到本机号码、认证不可用或您选择其他方式时，可使用图形验证码和短信注册，或使用已有账号的密码登录。拒绝一键认证不会阻止您选择其他可用方式。\n\n号码认证会使用网络访问、网络状态检查及在需要时切换Wi-Fi/蜂窝网络通道，以完成运营商验证。这些网络权限由Android系统管理。安装包还包含号码认证SDK声明的电话状态权限（READ_PHONE_STATE），用于认证所需的SIM/运营商状态处理；如系统请求授权，您可以拒绝并选择其他登录方式。应用自身不通过读取短信、通讯录或通话记录完成认证，不申请读取短信验证码的权限；您可手动填写短信验证码。文件访问仅在您使用相应功能时通过系统选择器授权。';

  @override
  String get privacySdkTitle => '三、第三方认证服务与信息清单';

  @override
  String get privacySdk =>
      '我们使用阿里云融合认证服务及其号码认证能力，由阿里巴巴云计算（北京）有限公司提供。\n\n阿里云融合认证SDK（接入版本1.2.16）：用于融合验证，按官方披露处理设备名称、设备型号和系统版本。当前应用启用运营商号码认证路径。\n\n号码认证服务/SDK：用于本机号码一键登录和认证，涉及手机号、IP地址、网络类型、网络状态、设备制造商、设备型号、手机操作系统、SIM卡状态及运营商类型，必要时切换Wi-Fi和蜂窝网络通道。手机号经服务端验证通过后，用于玄境云账号登录或注册。\n\n号码认证SDK集成三大运营商认证能力：\n• 中移互联网移动认证，运营主体为中移互联网有限公司。为本机号码识别、快速登录和安全风控，处理网络类型、网络地址、运营商类型、本机号码信息、SIM卡状态、手机设备类型、手机操作系统和硬件厂商。\n• 中国联通手机号一键登录，运营主体为中国联合网络通信集团有限公司。为本机号码识别、快速登录和安全风控，处理网络类型、网络地址、运营商类型、本机手机号、手机设备类型、手机操作系统和硬件厂商。\n• 天翼账号认证SDK，运营主体为天翼数字生活科技有限公司。为本机号码识别、快速登录和安全风控，处理注册手机号码、本机号码、网络连接类型、网络状态信息、网络地址、运营商类型、SIM卡状态、手机设备类型、手机操作系统和硬件厂商。具体认证使用与您当前号码对应的运营商能力，详情以授权页协议为准。\n\n手动短信注册使用阿里云号码认证服务的服务端短信发送和校验接口，处理您提交的手机号及验证码校验信息，以发送和核验短信。当前应用未启用融合SDK中的短信认证、图形认证或友盟风险插件节点；图形验证码由玄境云后端提供。第三方如何处理信息，请阅读以下官方政策及运营商授权页协议：';

  @override
  String get privacyNumberPolicy => '阿里云号码认证服务隐私权政策';

  @override
  String get privacySmsPolicy => '阿里云短信认证SDK隐私权政策（服务参考）';

  @override
  String get privacyMobilePolicy => '中国移动认证服务条款与隐私说明';

  @override
  String get privacyUnicomPolicy => '中国联通认证隐私权政策';

  @override
  String get privacyTelecomPolicy => '天翼账号服务与隐私协议';

  @override
  String get privacySharingTitle => '四、共享、转让与公开披露';

  @override
  String get privacySharing =>
      '为完成您选择的认证，我们会向上述认证服务提供方及相应运营商提供必要信息；为提供云建模、文件存储和任务处理服务，相关文件会交由服务使用的对象存储和计算资源处理。处理范围以实现您请求的功能所必需为限。\n\n我们不会出售您的个人信息，或为第三方营销而共享。除获得您的授权、履行法定义务或法律法规允许的其他情形外，不会向无关第三方共享、转让或公开披露。若发生运营主体变更并涉及个人信息转移，将依法告知您并要求接收方继续履行相应保护义务。';

  @override
  String get privacyStorageTitle => '五、信息保存与安全保护';

  @override
  String get privacyStorage =>
      '账号资料和业务数据保存在玄境云服务端，上传的图片保存在服务使用的对象存储中，工程模型由服务端保存。应用在本机安全保存会话和必要缓存；您可退出登录或清理缓存。\n\n我们仅在提供服务、处理您的请求和履行法定义务所需期限内保留相关信息。超出必要期限的信息将依法删除或匿名化。第三方认证服务的数据保存地点、期限和处理规则以其各自政策为准。本应用不会因号码认证主动将您的个人信息传输至境外。\n\n我们通过HTTPS传输、密码散列、账号访问控制、认证凭据时效与一次性校验等措施保护信息，限制未经授权的访问。任何系统都无法保证绝对安全；发生信息安全事件时，我们将依法采取处置措施，并履行必要的告知及报告义务。';

  @override
  String get privacyRightsTitle => '六、如何管理您的信息';

  @override
  String get privacyRights =>
      '您可在账户资料页查看和修改昵称、邮箱及单位；可在相应功能中管理自己上传的工程与模型，在缓存管理中清理本机缓存，或退出登录。您可以不勾选一键认证同意选项，或返回登录页选择其他方式；停止一键认证不影响此前已经依法进行的处理。\n\n如需查询、更正、复制、删除个人信息、撤回授权或申请注销账号，请联系baokx@seu.edu.cn。当前应用未提供自助账号注销入口，由运营团队核实身份后依法受理并回复。为保护账号安全，我们可能请求与申请相关的必要身份核验信息，不会要求提供登录密码或短信验证码。依法必须保留的信息或尚未完成的业务可能影响部分删除申请，我们会说明原因。';

  @override
  String get privacyMinorsTitle => '七、未成年人保护';

  @override
  String get privacyMinors =>
      '未成年人应在监护人指导下阅读本政策并使用相关服务。未满十四周岁的用户，应在取得监护人明确同意后使用涉及个人信息的功能。监护人如发现我们在未获适当授权的情况下处理了未成年人的信息，可通过隐私联系邮箱联系我们，我们将在核实后依法处理。';

  @override
  String get privacyUpdatesTitle => '八、政策更新与联系我们';

  @override
  String get privacyUpdates =>
      '我们可能随着功能或处理方式变化更新本政策，并在应用内展示最新版本。涉及处理目的、方式或个人信息种类等重大变化时，将依法进行提示并在必要时重新征求您的同意。\n\n如对本政策或个人信息处理有疑问、意见、投诉或权利请求，请联系：\n东南大学玄境创新团队\nbaokx@seu.edu.cn。';
}
