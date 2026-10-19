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
      '删除此工程及其全部模型、训练任务和数据集？此操作无法撤销，服务端正在运行的任务可能继续。';

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
}
