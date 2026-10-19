// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'XJICloud';

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
  String get welcome => '欢迎使用 XJICloud';

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
  String get viewerChooseCloud => '选择当前工程模型';

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
}
