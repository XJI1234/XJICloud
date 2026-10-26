import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In zh, this message translates to:
  /// **'玄境云'**
  String get appTitle;

  /// No description provided for @home.
  ///
  /// In zh, this message translates to:
  /// **'主页'**
  String get home;

  /// No description provided for @projects.
  ///
  /// In zh, this message translates to:
  /// **'工程管理'**
  String get projects;

  /// No description provided for @viewer.
  ///
  /// In zh, this message translates to:
  /// **'模型查看'**
  String get viewer;

  /// No description provided for @account.
  ///
  /// In zh, this message translates to:
  /// **'我的'**
  String get account;

  /// No description provided for @upload.
  ///
  /// In zh, this message translates to:
  /// **'上传'**
  String get upload;

  /// No description provided for @uploadImages.
  ///
  /// In zh, this message translates to:
  /// **'上传图片并训练'**
  String get uploadImages;

  /// No description provided for @uploadModel.
  ///
  /// In zh, this message translates to:
  /// **'上传 PLY/SPZ 模型'**
  String get uploadModel;

  /// No description provided for @loading.
  ///
  /// In zh, this message translates to:
  /// **'处理中'**
  String get loading;

  /// No description provided for @cancel.
  ///
  /// In zh, this message translates to:
  /// **'取消'**
  String get cancel;

  /// No description provided for @confirm.
  ///
  /// In zh, this message translates to:
  /// **'确认'**
  String get confirm;

  /// No description provided for @welcome.
  ///
  /// In zh, this message translates to:
  /// **'欢迎使用玄境云'**
  String get welcome;

  /// No description provided for @login.
  ///
  /// In zh, this message translates to:
  /// **'登录'**
  String get login;

  /// No description provided for @register.
  ///
  /// In zh, this message translates to:
  /// **'注册'**
  String get register;

  /// No description provided for @username.
  ///
  /// In zh, this message translates to:
  /// **'用户名'**
  String get username;

  /// No description provided for @password.
  ///
  /// In zh, this message translates to:
  /// **'密码'**
  String get password;

  /// No description provided for @confirmPassword.
  ///
  /// In zh, this message translates to:
  /// **'确认密码'**
  String get confirmPassword;

  /// No description provided for @displayName.
  ///
  /// In zh, this message translates to:
  /// **'显示名称（可选）'**
  String get displayName;

  /// No description provided for @captchaCode.
  ///
  /// In zh, this message translates to:
  /// **'验证码'**
  String get captchaCode;

  /// No description provided for @refreshCaptcha.
  ///
  /// In zh, this message translates to:
  /// **'刷新验证码'**
  String get refreshCaptcha;

  /// No description provided for @showPassword.
  ///
  /// In zh, this message translates to:
  /// **'显示密码'**
  String get showPassword;

  /// No description provided for @hidePassword.
  ///
  /// In zh, this message translates to:
  /// **'隐藏密码'**
  String get hidePassword;

  /// No description provided for @switchRegister.
  ///
  /// In zh, this message translates to:
  /// **'没有账号？注册'**
  String get switchRegister;

  /// No description provided for @switchLogin.
  ///
  /// In zh, this message translates to:
  /// **'已有账号？登录'**
  String get switchLogin;

  /// No description provided for @registrationSuccess.
  ///
  /// In zh, this message translates to:
  /// **'注册成功，请登录'**
  String get registrationSuccess;

  /// No description provided for @language.
  ///
  /// In zh, this message translates to:
  /// **'语言'**
  String get language;

  /// No description provided for @toggleLanguage.
  ///
  /// In zh, this message translates to:
  /// **'English'**
  String get toggleLanguage;

  /// No description provided for @help.
  ///
  /// In zh, this message translates to:
  /// **'帮助与关于'**
  String get help;

  /// No description provided for @helpContent.
  ///
  /// In zh, this message translates to:
  /// **'XJICloud 用于 3D 高斯模型管理与查看。账号信息来自本次登录会话。需要连接服务器才能登录或注册。会话过期后请重新登录。'**
  String get helpContent;

  /// No description provided for @logout.
  ///
  /// In zh, this message translates to:
  /// **'退出登录'**
  String get logout;

  /// No description provided for @sessionExpiry.
  ///
  /// In zh, this message translates to:
  /// **'会话到期时间'**
  String get sessionExpiry;

  /// No description provided for @userId.
  ///
  /// In zh, this message translates to:
  /// **'账号 ID'**
  String get userId;

  /// No description provided for @retry.
  ///
  /// In zh, this message translates to:
  /// **'重试'**
  String get retry;

  /// No description provided for @close.
  ///
  /// In zh, this message translates to:
  /// **'关闭'**
  String get close;

  /// No description provided for @requiredField.
  ///
  /// In zh, this message translates to:
  /// **'请填写此项'**
  String get requiredField;

  /// No description provided for @usernameValidation.
  ///
  /// In zh, this message translates to:
  /// **'用户名需要 3–64 个字符'**
  String get usernameValidation;

  /// No description provided for @passwordValidation.
  ///
  /// In zh, this message translates to:
  /// **'密码需要 6–128 个字符'**
  String get passwordValidation;

  /// No description provided for @confirmationValidation.
  ///
  /// In zh, this message translates to:
  /// **'两次输入的密码不一致'**
  String get confirmationValidation;

  /// No description provided for @displayNameValidation.
  ///
  /// In zh, this message translates to:
  /// **'显示名称最多 128 个字符'**
  String get displayNameValidation;

  /// No description provided for @errorNetwork.
  ///
  /// In zh, this message translates to:
  /// **'网络连接失败，请重试'**
  String get errorNetwork;

  /// No description provided for @errorCredentials.
  ///
  /// In zh, this message translates to:
  /// **'用户名或密码错误'**
  String get errorCredentials;

  /// No description provided for @errorCaptcha.
  ///
  /// In zh, this message translates to:
  /// **'验证码错误或已失效，请输入新验证码'**
  String get errorCaptcha;

  /// No description provided for @errorCaptchaRequired.
  ///
  /// In zh, this message translates to:
  /// **'请输入图片验证码'**
  String get errorCaptchaRequired;

  /// No description provided for @errorUsernameTaken.
  ///
  /// In zh, this message translates to:
  /// **'用户名已存在'**
  String get errorUsernameTaken;

  /// No description provided for @errorRateLimit.
  ///
  /// In zh, this message translates to:
  /// **'请求过于频繁，请稍后重试'**
  String get errorRateLimit;

  /// No description provided for @errorStorage.
  ///
  /// In zh, this message translates to:
  /// **'无法安全读取或清除会话。请重试；如仍失败，请在系统设置中清除应用数据。'**
  String get errorStorage;

  /// No description provided for @errorExpired.
  ///
  /// In zh, this message translates to:
  /// **'会话已到期，请重新登录'**
  String get errorExpired;

  /// No description provided for @errorRequest.
  ///
  /// In zh, this message translates to:
  /// **'操作失败，请重试'**
  String get errorRequest;

  /// No description provided for @errorForbidden.
  ///
  /// In zh, this message translates to:
  /// **'服务器拒绝访问，请重新登录或联系管理员'**
  String get errorForbidden;

  /// No description provided for @errorProtocol.
  ///
  /// In zh, this message translates to:
  /// **'服务器响应异常，请稍后重试'**
  String get errorProtocol;

  /// No description provided for @errorPreferences.
  ///
  /// In zh, this message translates to:
  /// **'语言设置未能保存，请重试'**
  String get errorPreferences;

  /// No description provided for @configurationError.
  ///
  /// In zh, this message translates to:
  /// **'服务地址配置无效'**
  String get configurationError;

  /// No description provided for @viewLicenses.
  ///
  /// In zh, this message translates to:
  /// **'开源许可证'**
  String get viewLicenses;

  /// No description provided for @sdkAbout.
  ///
  /// In zh, this message translates to:
  /// **'Native3DGS SDK 0.2.2 · XJICloud 安卓客户端'**
  String get sdkAbout;

  /// No description provided for @currentProject.
  ///
  /// In zh, this message translates to:
  /// **'当前工程'**
  String get currentProject;

  /// No description provided for @recentProjects.
  ///
  /// In zh, this message translates to:
  /// **'最近打开'**
  String get recentProjects;

  /// No description provided for @createProject.
  ///
  /// In zh, this message translates to:
  /// **'创建工程'**
  String get createProject;

  /// No description provided for @firstProject.
  ///
  /// In zh, this message translates to:
  /// **'创建第一个工程'**
  String get firstProject;

  /// No description provided for @projectName.
  ///
  /// In zh, this message translates to:
  /// **'工程名称'**
  String get projectName;

  /// No description provided for @projectDescription.
  ///
  /// In zh, this message translates to:
  /// **'描述'**
  String get projectDescription;

  /// No description provided for @saveProject.
  ///
  /// In zh, this message translates to:
  /// **'保存'**
  String get saveProject;

  /// No description provided for @editProject.
  ///
  /// In zh, this message translates to:
  /// **'编辑工程'**
  String get editProject;

  /// No description provided for @deleteProject.
  ///
  /// In zh, this message translates to:
  /// **'删除工程'**
  String get deleteProject;

  /// No description provided for @setCurrentProject.
  ///
  /// In zh, this message translates to:
  /// **'设为当前工程'**
  String get setCurrentProject;

  /// No description provided for @projectCreated.
  ///
  /// In zh, this message translates to:
  /// **'创建时间'**
  String get projectCreated;

  /// No description provided for @projectEmpty.
  ///
  /// In zh, this message translates to:
  /// **'暂无工程'**
  String get projectEmpty;

  /// No description provided for @selectProject.
  ///
  /// In zh, this message translates to:
  /// **'选择工程以查看详情。'**
  String get selectProject;

  /// No description provided for @projectUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'该工程不可用，请刷新工程列表。'**
  String get projectUnavailable;

  /// No description provided for @projectValidation.
  ///
  /// In zh, this message translates to:
  /// **'请输入 1–256 字的工程名称，描述最多 2000 字。'**
  String get projectValidation;

  /// No description provided for @workspaceStorage.
  ///
  /// In zh, this message translates to:
  /// **'无法保存或读取工作区，请重试。'**
  String get workspaceStorage;

  /// No description provided for @projectDeletedStorage.
  ///
  /// In zh, this message translates to:
  /// **'工程已删除，但本地工作区保存失败；请刷新以同步。'**
  String get projectDeletedStorage;

  /// No description provided for @deleteProjectMessage.
  ///
  /// In zh, this message translates to:
  /// **'删除此工程及其全部模型、训练任务和数据集？此操作无法撤销，服务端正在运行的任务可能继续。 本地活动传输会先停止。'**
  String get deleteProjectMessage;

  /// No description provided for @discardChanges.
  ///
  /// In zh, this message translates to:
  /// **'放弃修改？'**
  String get discardChanges;

  /// No description provided for @discardChangesMessage.
  ///
  /// In zh, this message translates to:
  /// **'当前修改尚未保存。'**
  String get discardChangesMessage;

  /// No description provided for @keepEditing.
  ///
  /// In zh, this message translates to:
  /// **'继续编辑'**
  String get keepEditing;

  /// No description provided for @discard.
  ///
  /// In zh, this message translates to:
  /// **'放弃修改'**
  String get discard;

  /// No description provided for @projectsIntro.
  ///
  /// In zh, this message translates to:
  /// **'用工程整理模型与训练素材。'**
  String get projectsIntro;

  /// No description provided for @noRecentProjects.
  ///
  /// In zh, this message translates to:
  /// **'打开过的工程将显示在这里。'**
  String get noRecentProjects;

  /// No description provided for @noCurrentProject.
  ///
  /// In zh, this message translates to:
  /// **'选择工程以继续。'**
  String get noCurrentProject;

  /// No description provided for @openProject.
  ///
  /// In zh, this message translates to:
  /// **'打开工程'**
  String get openProject;

  /// No description provided for @models.
  ///
  /// In zh, this message translates to:
  /// **'模型资产'**
  String get models;

  /// No description provided for @modelEmpty.
  ///
  /// In zh, this message translates to:
  /// **'此工程暂无模型'**
  String get modelEmpty;

  /// No description provided for @modelUploadPolicy.
  ///
  /// In zh, this message translates to:
  /// **'支持 PLY/SPZ，文件大小 1 字节至 2 GiB。选择后将创建私有副本用于安全续传。'**
  String get modelUploadPolicy;

  /// No description provided for @modelValidation.
  ///
  /// In zh, this message translates to:
  /// **'请选择有效的 PLY/SPZ 文件（1 字节至 2 GiB）。空间不足时请先释放设备存储。'**
  String get modelValidation;

  /// No description provided for @modelDownload.
  ///
  /// In zh, this message translates to:
  /// **'下载到私有缓存'**
  String get modelDownload;

  /// No description provided for @modelCached.
  ///
  /// In zh, this message translates to:
  /// **'已缓存'**
  String get modelCached;

  /// No description provided for @cacheRemove.
  ///
  /// In zh, this message translates to:
  /// **'删除此缓存副本'**
  String get cacheRemove;

  /// No description provided for @modelCreated.
  ///
  /// In zh, this message translates to:
  /// **'创建时间'**
  String get modelCreated;

  /// No description provided for @modelUpdated.
  ///
  /// In zh, this message translates to:
  /// **'更新时间'**
  String get modelUpdated;

  /// No description provided for @deleteModel.
  ///
  /// In zh, this message translates to:
  /// **'删除云模型'**
  String get deleteModel;

  /// No description provided for @deleteModelMessage.
  ///
  /// In zh, this message translates to:
  /// **'删除此云模型并失效本账号缓存？此操作无法撤销。'**
  String get deleteModelMessage;

  /// No description provided for @pauseTransfer.
  ///
  /// In zh, this message translates to:
  /// **'暂停传输'**
  String get pauseTransfer;

  /// No description provided for @resumeTransfer.
  ///
  /// In zh, this message translates to:
  /// **'核对断点并续传'**
  String get resumeTransfer;

  /// No description provided for @reselectSource.
  ///
  /// In zh, this message translates to:
  /// **'重选相同源文件'**
  String get reselectSource;

  /// No description provided for @abandonUpload.
  ///
  /// In zh, this message translates to:
  /// **'取消此上传'**
  String get abandonUpload;

  /// No description provided for @abandonUploadMessage.
  ///
  /// In zh, this message translates to:
  /// **'停止本地跟踪并取消上传会话。若服务器已完成模型，云模型会保留，请在列表中核对。'**
  String get abandonUploadMessage;

  /// No description provided for @uploadReady.
  ///
  /// In zh, this message translates to:
  /// **'准备上传'**
  String get uploadReady;

  /// No description provided for @uploadPaused.
  ///
  /// In zh, this message translates to:
  /// **'已暂停，可核对断点后续传'**
  String get uploadPaused;

  /// No description provided for @uploadCompleting.
  ///
  /// In zh, this message translates to:
  /// **'正在确认完成'**
  String get uploadCompleting;

  /// No description provided for @uploadUncertain.
  ///
  /// In zh, this message translates to:
  /// **'完成结果待核对'**
  String get uploadUncertain;

  /// No description provided for @uploadCompleted.
  ///
  /// In zh, this message translates to:
  /// **'服务器已确认完成，请核对最新模型列表'**
  String get uploadCompleted;

  /// No description provided for @uploadPreparing.
  ///
  /// In zh, this message translates to:
  /// **'正在读取文件并准备私有副本'**
  String get uploadPreparing;

  /// No description provided for @completionUncertain.
  ///
  /// In zh, this message translates to:
  /// **'服务器完成结果未确认。列表已刷新，请按模型标识与创建时间核对。不会自动重复上传或选择同名模型。'**
  String get completionUncertain;

  /// No description provided for @uploadCreateUncertain.
  ///
  /// In zh, this message translates to:
  /// **'创建上传会话的响应未确认，未自动重试。请核对工程模型；未完成会话会由服务器过期清理。'**
  String get uploadCreateUncertain;

  /// No description provided for @retryCompletion.
  ///
  /// In zh, this message translates to:
  /// **'再次请求完成'**
  String get retryCompletion;

  /// No description provided for @retryCompletionMessage.
  ///
  /// In zh, this message translates to:
  /// **'请先核对模型列表。当前服务器重复完成可能返回“模型不存在”；仅在你确认后再次请求。不会新建上传。'**
  String get retryCompletionMessage;

  /// No description provided for @reconcileModel.
  ///
  /// In zh, this message translates to:
  /// **'确认为此次上传结果'**
  String get reconcileModel;

  /// No description provided for @reconcileModelMessage.
  ///
  /// In zh, this message translates to:
  /// **'请核对模型标识、文件名、大小和创建时间，确认这是此次上传生成的资产。'**
  String get reconcileModelMessage;

  /// No description provided for @sourceChanged.
  ///
  /// In zh, this message translates to:
  /// **'源文件丢失或内容已改变。请重选完全相同的文件；不同内容不能续传。'**
  String get sourceChanged;

  /// No description provided for @cacheTitle.
  ///
  /// In zh, this message translates to:
  /// **'模型缓存'**
  String get cacheTitle;

  /// No description provided for @cacheDescription.
  ///
  /// In zh, this message translates to:
  /// **'仅管理当前账号的模型下载。正在使用的文件会保留，源文件与上传副本不会被清除。'**
  String get cacheDescription;

  /// No description provided for @cacheClear.
  ///
  /// In zh, this message translates to:
  /// **'清除可释放缓存'**
  String get cacheClear;

  /// No description provided for @cacheFreed.
  ///
  /// In zh, this message translates to:
  /// **'已释放字节'**
  String get cacheFreed;

  /// No description provided for @cacheBytes.
  ///
  /// In zh, this message translates to:
  /// **'缓存字节'**
  String get cacheBytes;

  /// No description provided for @cacheFull.
  ///
  /// In zh, this message translates to:
  /// **'缓存配额不足，或所有可释放文件正在使用。请先清理缓存或关闭模型。'**
  String get cacheFull;

  /// No description provided for @cacheInUse.
  ///
  /// In zh, this message translates to:
  /// **'此缓存正在使用，请关闭模型后重试。'**
  String get cacheInUse;

  /// No description provided for @modelNotFound.
  ///
  /// In zh, this message translates to:
  /// **'模型或上传会话不存在，可能已删除或过期。请刷新工程列表后核对。'**
  String get modelNotFound;

  /// No description provided for @modelDeletedStorage.
  ///
  /// In zh, this message translates to:
  /// **'云模型已删除，但本地缓存清理失败，请重试清理缓存。'**
  String get modelDeletedStorage;

  /// No description provided for @uploadCompletedStorage.
  ///
  /// In zh, this message translates to:
  /// **'服务器已确认完成，但本地记录保存失败。请核对列表后结束本地跟踪。'**
  String get uploadCompletedStorage;

  /// No description provided for @cancelledTransfer.
  ///
  /// In zh, this message translates to:
  /// **'传输已暂停或取消，未提交不完整缓存。'**
  String get cancelledTransfer;

  /// No description provided for @finishUpload.
  ///
  /// In zh, this message translates to:
  /// **'结束本地跟踪'**
  String get finishUpload;

  /// No description provided for @uploadDestination.
  ///
  /// In zh, this message translates to:
  /// **'固定目标工程'**
  String get uploadDestination;

  /// No description provided for @cancelDownload.
  ///
  /// In zh, this message translates to:
  /// **'取消下载'**
  String get cancelDownload;

  /// No description provided for @modelUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'所选模型不可用，请重新刷新并核对。'**
  String get modelUnavailable;

  /// No description provided for @sourceBusy.
  ///
  /// In zh, this message translates to:
  /// **'系统文件选择器仍在使用，请先完成或取消上一次选择。'**
  String get sourceBusy;

  /// No description provided for @chooseImages.
  ///
  /// In zh, this message translates to:
  /// **'选择多张图片'**
  String get chooseImages;

  /// No description provided for @chooseImageFolder.
  ///
  /// In zh, this message translates to:
  /// **'选择图片文件夹'**
  String get chooseImageFolder;

  /// No description provided for @datasetPolicy.
  ///
  /// In zh, this message translates to:
  /// **'选择 1–9999 张 JPG、PNG 或 WebP 图片。原图逐张上传，不压缩、不转码；切到后台会暂停发送。'**
  String get datasetPolicy;

  /// No description provided for @datasetName.
  ///
  /// In zh, this message translates to:
  /// **'训练任务名称'**
  String get datasetName;

  /// No description provided for @datasetPreview.
  ///
  /// In zh, this message translates to:
  /// **'归档预览'**
  String get datasetPreview;

  /// No description provided for @datasetImageCount.
  ///
  /// In zh, this message translates to:
  /// **'图片数量'**
  String get datasetImageCount;

  /// No description provided for @datasetTotalBytes.
  ///
  /// In zh, this message translates to:
  /// **'上传总字节'**
  String get datasetTotalBytes;

  /// No description provided for @datasetPaused.
  ///
  /// In zh, this message translates to:
  /// **'本地上传已暂停；服务端状态仍以任务快照为准。'**
  String get datasetPaused;

  /// No description provided for @datasetResume.
  ///
  /// In zh, this message translates to:
  /// **'查询任务并继续'**
  String get datasetResume;

  /// No description provided for @datasetRestart.
  ///
  /// In zh, this message translates to:
  /// **'取消旧任务并重新提交'**
  String get datasetRestart;

  /// No description provided for @datasetRestartMessage.
  ///
  /// In zh, this message translates to:
  /// **'签名无法补签。将先查询并取消仍在上传的旧任务，再用这些素材创建新任务并全部重传。若源文件已丢失，请重新选图。'**
  String get datasetRestartMessage;

  /// No description provided for @datasetRestartRequired.
  ///
  /// In zh, this message translates to:
  /// **'重启后签名不可恢复，或签名已过期。先查询任务；仍在上传时，确认取消旧任务再重新提交。'**
  String get datasetRestartRequired;

  /// No description provided for @datasetCreateUncertain.
  ///
  /// In zh, this message translates to:
  /// **'创建响应不确定，未自动重试。请刷新任务列表，核对任务 ID、名称和创建时间，再明确选择此次任务。无法自动推断身份。'**
  String get datasetCreateUncertain;

  /// No description provided for @datasetValidation.
  ///
  /// In zh, this message translates to:
  /// **'请选择 1–9999 张非空 JPG、PNG 或 WebP 图片；文件名、格式和 MIME 必须一致。不支持 HEIC。'**
  String get datasetValidation;

  /// No description provided for @datasetDiscard.
  ///
  /// In zh, this message translates to:
  /// **'取消上传并清除素材'**
  String get datasetDiscard;

  /// No description provided for @datasetDiscardMessage.
  ///
  /// In zh, this message translates to:
  /// **'停止本地发送，并查询和取消此次服务端任务。未提交素材将被清除。创建结果不确定时须先核对任务身份。'**
  String get datasetDiscardMessage;

  /// No description provided for @datasetLeave.
  ///
  /// In zh, this message translates to:
  /// **'暂停并离开'**
  String get datasetLeave;

  /// No description provided for @datasetLeaveMessage.
  ///
  /// In zh, this message translates to:
  /// **'素材与已确认上传项会保留。后台不会继续发送；返回后先查询任务再恢复。'**
  String get datasetLeaveMessage;

  /// No description provided for @datasetConfirmCandidate.
  ///
  /// In zh, this message translates to:
  /// **'确认为此次创建的任务'**
  String get datasetConfirmCandidate;

  /// No description provided for @datasetCandidateMessage.
  ///
  /// In zh, this message translates to:
  /// **'请核对任务 ID、名称和创建时间。此操作仅关联记录；不会推断上传对象或恢复丢失的签名。'**
  String get datasetCandidateMessage;

  /// No description provided for @datasetNoCandidate.
  ///
  /// In zh, this message translates to:
  /// **'已核对列表，未找到此次任务'**
  String get datasetNoCandidate;

  /// No description provided for @datasetNoCandidateMessage.
  ///
  /// In zh, this message translates to:
  /// **'仅在已核对完整任务列表并确认此次创建未成功时继续。将清除此创建意图并保留素材，随后可手动提交新任务。'**
  String get datasetNoCandidateMessage;

  /// No description provided for @datasetCompleteRetry.
  ///
  /// In zh, this message translates to:
  /// **'核对后再次请求入队'**
  String get datasetCompleteRetry;

  /// No description provided for @datasetCompleteMessage.
  ///
  /// In zh, this message translates to:
  /// **'完成请求并非幂等。仅当所有图片和 manifest 已确认上传、最新查询仍为 UPLOADING 时再次请求；客户端不会自动重试。'**
  String get datasetCompleteMessage;

  /// No description provided for @trainingJobs.
  ///
  /// In zh, this message translates to:
  /// **'训练任务'**
  String get trainingJobs;

  /// No description provided for @noTrainingJobs.
  ///
  /// In zh, this message translates to:
  /// **'此工程还没有训练任务'**
  String get noTrainingJobs;

  /// No description provided for @jobId.
  ///
  /// In zh, this message translates to:
  /// **'任务 ID'**
  String get jobId;

  /// No description provided for @jobCancel.
  ///
  /// In zh, this message translates to:
  /// **'取消任务'**
  String get jobCancel;

  /// No description provided for @jobCancelMessage.
  ///
  /// In zh, this message translates to:
  /// **'取消不能保证立即中断 Worker，任务仍可能随后完成。页面会继续查询真实状态。'**
  String get jobCancelMessage;

  /// No description provided for @jobDelete.
  ///
  /// In zh, this message translates to:
  /// **'删除任务记录'**
  String get jobDelete;

  /// No description provided for @jobDeleteMessage.
  ///
  /// In zh, this message translates to:
  /// **'删除此终态任务记录及本地下载副本？'**
  String get jobDeleteMessage;

  /// No description provided for @jobStateError.
  ///
  /// In zh, this message translates to:
  /// **'任务状态已变化，请查询最新任务。'**
  String get jobStateError;

  /// No description provided for @outputDownload.
  ///
  /// In zh, this message translates to:
  /// **'下载训练产物'**
  String get outputDownload;

  /// No description provided for @outputDownloaded.
  ///
  /// In zh, this message translates to:
  /// **'训练产物已下载到私有存储'**
  String get outputDownloaded;

  /// No description provided for @outputUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'服务端尚未提供训练产物下载地址'**
  String get outputUnavailable;

  /// No description provided for @outputUrlExpired.
  ///
  /// In zh, this message translates to:
  /// **'产物下载签名已失效。当前服务端可能重复返回旧地址，重新查询不能保证补签；请联系服务端维护者。'**
  String get outputUrlExpired;

  /// No description provided for @jobPending.
  ///
  /// In zh, this message translates to:
  /// **'待处理'**
  String get jobPending;

  /// No description provided for @jobUploading.
  ///
  /// In zh, this message translates to:
  /// **'上传中'**
  String get jobUploading;

  /// No description provided for @jobQueued.
  ///
  /// In zh, this message translates to:
  /// **'排队中'**
  String get jobQueued;

  /// No description provided for @jobRunning.
  ///
  /// In zh, this message translates to:
  /// **'训练中'**
  String get jobRunning;

  /// No description provided for @jobCompleted.
  ///
  /// In zh, this message translates to:
  /// **'已完成'**
  String get jobCompleted;

  /// No description provided for @jobFailed.
  ///
  /// In zh, this message translates to:
  /// **'失败'**
  String get jobFailed;

  /// No description provided for @jobCancelled.
  ///
  /// In zh, this message translates to:
  /// **'已取消'**
  String get jobCancelled;

  /// No description provided for @viewerOpenLocal.
  ///
  /// In zh, this message translates to:
  /// **'打开本机 PLY/SPZ'**
  String get viewerOpenLocal;

  /// No description provided for @viewerChooseCloud.
  ///
  /// In zh, this message translates to:
  /// **'浏览云端模型'**
  String get viewerChooseCloud;

  /// No description provided for @viewerOpen.
  ///
  /// In zh, this message translates to:
  /// **'查看模型'**
  String get viewerOpen;

  /// No description provided for @viewerTools.
  ///
  /// In zh, this message translates to:
  /// **'查看工具'**
  String get viewerTools;

  /// No description provided for @viewerFixed.
  ///
  /// In zh, this message translates to:
  /// **'固定模式'**
  String get viewerFixed;

  /// No description provided for @viewerFree.
  ///
  /// In zh, this message translates to:
  /// **'自由模式'**
  String get viewerFree;

  /// No description provided for @viewerFit.
  ///
  /// In zh, this message translates to:
  /// **'适配'**
  String get viewerFit;

  /// No description provided for @viewerReset.
  ///
  /// In zh, this message translates to:
  /// **'重置'**
  String get viewerReset;

  /// No description provided for @viewerFlip.
  ///
  /// In zh, this message translates to:
  /// **'Y 翻转'**
  String get viewerFlip;

  /// No description provided for @viewerZoomIn.
  ///
  /// In zh, this message translates to:
  /// **'放大'**
  String get viewerZoomIn;

  /// No description provided for @viewerZoomOut.
  ///
  /// In zh, this message translates to:
  /// **'缩小'**
  String get viewerZoomOut;

  /// No description provided for @viewerFullscreen.
  ///
  /// In zh, this message translates to:
  /// **'全屏'**
  String get viewerFullscreen;

  /// No description provided for @viewerExitFullscreen.
  ///
  /// In zh, this message translates to:
  /// **'退出全屏'**
  String get viewerExitFullscreen;

  /// No description provided for @viewerClose.
  ///
  /// In zh, this message translates to:
  /// **'关闭模型'**
  String get viewerClose;

  /// No description provided for @viewerCancel.
  ///
  /// In zh, this message translates to:
  /// **'取消加载'**
  String get viewerCancel;

  /// No description provided for @viewerEmpty.
  ///
  /// In zh, this message translates to:
  /// **'打开本机文件，或从当前工程选择一个模型。'**
  String get viewerEmpty;

  /// No description provided for @viewerDownload.
  ///
  /// In zh, this message translates to:
  /// **'下载 / 准备本机文件'**
  String get viewerDownload;

  /// No description provided for @viewerDecode.
  ///
  /// In zh, this message translates to:
  /// **'解码'**
  String get viewerDecode;

  /// No description provided for @viewerUpload.
  ///
  /// In zh, this message translates to:
  /// **'GPU 上传 / 等待首帧'**
  String get viewerUpload;

  /// No description provided for @viewerFirstFrame.
  ///
  /// In zh, this message translates to:
  /// **'首帧就绪'**
  String get viewerFirstFrame;

  /// No description provided for @viewerRecover.
  ///
  /// In zh, this message translates to:
  /// **'恢复画布'**
  String get viewerRecover;

  /// No description provided for @viewerClosing.
  ///
  /// In zh, this message translates to:
  /// **'正在关闭模型'**
  String get viewerClosing;

  /// No description provided for @viewerError.
  ///
  /// In zh, this message translates to:
  /// **'模型加载或渲染失败。可取消或重新选择有效 PLY/SPZ；已有场景会保留。'**
  String get viewerError;

  /// No description provided for @viewerUnsupported.
  ///
  /// In zh, this message translates to:
  /// **'当前平台不支持原生查看器。需要 Android 10+、arm64、Vulkan 1.1 和 4KB 内存页。'**
  String get viewerUnsupported;

  /// No description provided for @viewerConfigFit.
  ///
  /// In zh, this message translates to:
  /// **'缺少兼容视角元数据，已自动取景；不会覆盖云端配置。'**
  String get viewerConfigFit;

  /// No description provided for @viewerForward.
  ///
  /// In zh, this message translates to:
  /// **'前进'**
  String get viewerForward;

  /// No description provided for @viewerBackward.
  ///
  /// In zh, this message translates to:
  /// **'后退'**
  String get viewerBackward;

  /// No description provided for @viewerLeft.
  ///
  /// In zh, this message translates to:
  /// **'左移'**
  String get viewerLeft;

  /// No description provided for @viewerRight.
  ///
  /// In zh, this message translates to:
  /// **'右移'**
  String get viewerRight;

  /// No description provided for @viewerUp.
  ///
  /// In zh, this message translates to:
  /// **'上移'**
  String get viewerUp;

  /// No description provided for @viewerDown.
  ///
  /// In zh, this message translates to:
  /// **'下移'**
  String get viewerDown;

  /// No description provided for @viewerOutput.
  ///
  /// In zh, this message translates to:
  /// **'打开训练产物'**
  String get viewerOutput;

  /// No description provided for @viewerOutputNote.
  ///
  /// In zh, this message translates to:
  /// **'训练产物须为有效 Gaussian PLY；任务完成不代表格式兼容。'**
  String get viewerOutputNote;

  /// No description provided for @viewerCloudTitle.
  ///
  /// In zh, this message translates to:
  /// **'云端模型'**
  String get viewerCloudTitle;

  /// No description provided for @viewerLocalTitle.
  ///
  /// In zh, this message translates to:
  /// **'本机模型'**
  String get viewerLocalTitle;

  /// No description provided for @viewerRefresh.
  ///
  /// In zh, this message translates to:
  /// **'刷新模型'**
  String get viewerRefresh;

  /// No description provided for @viewerProject.
  ///
  /// In zh, this message translates to:
  /// **'所属工程'**
  String get viewerProject;

  /// No description provided for @fusionLogin.
  ///
  /// In zh, this message translates to:
  /// **'本机号码一键登录 / 注册'**
  String get fusionLogin;

  /// No description provided for @passwordLogin.
  ///
  /// In zh, this message translates to:
  /// **'密码登录'**
  String get passwordLogin;

  /// No description provided for @phoneRegistration.
  ///
  /// In zh, this message translates to:
  /// **'手机号注册'**
  String get phoneRegistration;

  /// No description provided for @privacyConsent.
  ///
  /// In zh, this message translates to:
  /// **'我同意号码认证，并已阅读'**
  String get privacyConsent;

  /// No description provided for @fusionPrivacyPurpose.
  ///
  /// In zh, this message translates to:
  /// **'使用本机号码登录或注册。经您同意后，将使用手机号、网络和设备信息完成认证，并请您确认运营商协议。'**
  String get fusionPrivacyPurpose;

  /// No description provided for @fusionPrivacyPolicy.
  ///
  /// In zh, this message translates to:
  /// **'隐私权政策'**
  String get fusionPrivacyPolicy;

  /// No description provided for @privacyRequired.
  ///
  /// In zh, this message translates to:
  /// **'请先阅读数据处理说明并勾选同意，再使用一键认证。'**
  String get privacyRequired;

  /// No description provided for @fusionUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'当前设备或服务暂不支持一键认证，可继续手机号注册或密码登录。'**
  String get fusionUnavailable;

  /// No description provided for @fusionFailed.
  ///
  /// In zh, this message translates to:
  /// **'号码认证未完成，可继续手机号注册或稍后重试。'**
  String get fusionFailed;

  /// No description provided for @fusionBackendFailed.
  ///
  /// In zh, this message translates to:
  /// **'认证服务暂未完成登录，可使用手机号注册或稍后重试。'**
  String get fusionBackendFailed;

  /// No description provided for @phoneNumber.
  ///
  /// In zh, this message translates to:
  /// **'大陆手机号'**
  String get phoneNumber;

  /// No description provided for @phoneValidation.
  ///
  /// In zh, this message translates to:
  /// **'请输入11位大陆手机号。'**
  String get phoneValidation;

  /// No description provided for @sendSms.
  ///
  /// In zh, this message translates to:
  /// **'发送短信验证码'**
  String get sendSms;

  /// No description provided for @resendSms.
  ///
  /// In zh, this message translates to:
  /// **'重新发送短信验证码'**
  String get resendSms;

  /// No description provided for @smsCode.
  ///
  /// In zh, this message translates to:
  /// **'6位短信验证码'**
  String get smsCode;

  /// No description provided for @smsValidation.
  ///
  /// In zh, this message translates to:
  /// **'请输入6位短信验证码。'**
  String get smsValidation;

  /// No description provided for @smsRequired.
  ///
  /// In zh, this message translates to:
  /// **'请先获取短信验证码验证手机号。'**
  String get smsRequired;

  /// No description provided for @registrationPasswordValidation.
  ///
  /// In zh, this message translates to:
  /// **'密码为8–64个字符，UTF-8编码不超过72字节。'**
  String get registrationPasswordValidation;

  /// No description provided for @registrationValidation.
  ///
  /// In zh, this message translates to:
  /// **'请检查手机号、短信验证码、密码及确认密码。'**
  String get registrationValidation;

  /// No description provided for @completeProfileTitle.
  ///
  /// In zh, this message translates to:
  /// **'完善账户资料'**
  String get completeProfileTitle;

  /// No description provided for @profileIntro.
  ///
  /// In zh, this message translates to:
  /// **'补全昵称、邮箱和单位后即可使用云服务。'**
  String get profileIntro;

  /// No description provided for @profileEmail.
  ///
  /// In zh, this message translates to:
  /// **'邮箱'**
  String get profileEmail;

  /// No description provided for @profileOrganization.
  ///
  /// In zh, this message translates to:
  /// **'单位'**
  String get profileOrganization;

  /// No description provided for @saveProfile.
  ///
  /// In zh, this message translates to:
  /// **'保存资料'**
  String get saveProfile;

  /// No description provided for @editProfile.
  ///
  /// In zh, this message translates to:
  /// **'账户资料'**
  String get editProfile;

  /// No description provided for @profileValidation.
  ///
  /// In zh, this message translates to:
  /// **'请填写有效昵称、邮箱和单位。'**
  String get profileValidation;

  /// No description provided for @profileRequiredError.
  ///
  /// In zh, this message translates to:
  /// **'请先完善账户资料。'**
  String get profileRequiredError;

  /// No description provided for @phoneRegistered.
  ///
  /// In zh, this message translates to:
  /// **'该手机号已注册，请使用密码登录。'**
  String get phoneRegistered;

  /// No description provided for @phoneUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'未识别到本机号码'**
  String get phoneUnavailable;

  /// No description provided for @policyUnavailable.
  ///
  /// In zh, this message translates to:
  /// **'暂时无法打开隐私权政策，请稍后重试。'**
  String get policyUnavailable;

  /// No description provided for @privacyEffectiveDate.
  ///
  /// In zh, this message translates to:
  /// **'更新日期：2026年10月27日\n生效日期：2026年10月27日'**
  String get privacyEffectiveDate;

  /// No description provided for @privacyIntroduction.
  ///
  /// In zh, this message translates to:
  /// **'玄境云（XJICloud）由东南大学玄境创新团队运营。本政策说明我们如何在账号认证及云建模服务中处理您的个人信息，以及您可以如何管理信息。请在使用相关功能前阅读本政策，尤其是手机号、上传文件和第三方认证服务的说明。\n\n运营主体：东南大学玄境创新团队\n隐私联系邮箱：baokx@seu.edu.cn'**
  String get privacyIntroduction;

  /// No description provided for @privacyCollectionTitle.
  ///
  /// In zh, this message translates to:
  /// **'一、我们如何收集和使用信息'**
  String get privacyCollectionTitle;

  /// No description provided for @privacyCollection.
  ///
  /// In zh, this message translates to:
  /// **'账号注册与登录：您通过短信注册时，我们处理您输入的手机号、图形验证码、短信验证码和设置的密码，以核验手机号、创建账号和保障登录安全。密码在服务端以不可逆散列形式保存。通过本机号码认证时，我们仅以认证服务核验通过的手机号登录或创建同一账号。\n\n资料完善：您提供的昵称、邮箱和单位用于账户资料展示与管理；填写邮箱不代表已经验证邮箱所有权。\n\n云建模与模型查看：只有在您主动选择、上传或打开文件时，应用才会处理对应的照片、图片数据集、PLY/SPZ模型及相关文件名称、大小和任务信息，用于上传、训练、查看和下载。请勿上传未经授权的他人个人信息。使用系统文件选择器不代表授权访问全部文件。\n\n安全与会话：为维持登录和控制访问，设备会安全保存登录凭证及必要账号信息；服务端处理请求IP、认证状态和必要的操作记录，用于限流、识别异常、处理故障及防止重复使用认证凭据。我们不会将验证码或认证凭据用于广告营销。'**
  String get privacyCollection;

  /// No description provided for @privacyChoiceTitle.
  ///
  /// In zh, this message translates to:
  /// **'二、您的选择与认证权限'**
  String get privacyChoiceTitle;

  /// No description provided for @privacyChoice.
  ///
  /// In zh, this message translates to:
  /// **'本机号码一键登录/注册是可选功能。仅当您主动勾选同意本政策并点击一键登录/注册后，我们才检查SIM可用状态并启动后续号码认证；未勾选时不初始化融合认证SDK。运营商授权页还会单独展示相关协议，由您自主决定是否接受。仅阅读政策不会代您同意。\n\n未识别到本机号码、认证不可用或您选择其他方式时，可使用图形验证码和短信注册，或使用已有账号的密码登录。拒绝一键认证不会阻止您选择其他可用方式。\n\n号码认证会使用网络访问、网络状态检查及在需要时切换Wi-Fi/蜂窝网络通道，以完成运营商验证。这些网络权限由Android系统管理。安装包还包含号码认证SDK声明的电话状态权限（READ_PHONE_STATE），用于认证所需的SIM/运营商状态处理；如系统请求授权，您可以拒绝并选择其他登录方式。应用自身不通过读取短信、通讯录或通话记录完成认证，不申请读取短信验证码的权限；您可手动填写短信验证码。文件访问仅在您使用相应功能时通过系统选择器授权。'**
  String get privacyChoice;

  /// No description provided for @privacySdkTitle.
  ///
  /// In zh, this message translates to:
  /// **'三、第三方认证服务与信息清单'**
  String get privacySdkTitle;

  /// No description provided for @privacySdk.
  ///
  /// In zh, this message translates to:
  /// **'我们使用阿里云融合认证服务及其号码认证能力，由阿里巴巴云计算（北京）有限公司提供。\n\n阿里云融合认证SDK（接入版本1.2.16）：用于融合验证，按官方披露处理设备名称、设备型号和系统版本。当前应用启用运营商号码认证路径。\n\n号码认证服务/SDK：用于本机号码一键登录和认证，涉及手机号、IP地址、网络类型、网络状态、设备制造商、设备型号、手机操作系统、SIM卡状态及运营商类型，必要时切换Wi-Fi和蜂窝网络通道。手机号经服务端验证通过后，用于玄境云账号登录或注册。\n\n号码认证SDK集成三大运营商认证能力：\n• 中移互联网移动认证，运营主体为中移互联网有限公司。为本机号码识别、快速登录和安全风控，处理网络类型、网络地址、运营商类型、本机号码信息、SIM卡状态、手机设备类型、手机操作系统和硬件厂商。\n• 中国联通手机号一键登录，运营主体为中国联合网络通信集团有限公司。为本机号码识别、快速登录和安全风控，处理网络类型、网络地址、运营商类型、本机手机号、手机设备类型、手机操作系统和硬件厂商。\n• 天翼账号认证SDK，运营主体为天翼数字生活科技有限公司。为本机号码识别、快速登录和安全风控，处理注册手机号码、本机号码、网络连接类型、网络状态信息、网络地址、运营商类型、SIM卡状态、手机设备类型、手机操作系统和硬件厂商。具体认证使用与您当前号码对应的运营商能力，详情以授权页协议为准。\n\n手动短信注册使用阿里云号码认证服务的服务端短信发送和校验接口，处理您提交的手机号及验证码校验信息，以发送和核验短信。当前应用未启用融合SDK中的短信认证、图形认证或友盟风险插件节点；图形验证码由玄境云后端提供。第三方如何处理信息，请阅读以下官方政策及运营商授权页协议：'**
  String get privacySdk;

  /// No description provided for @privacyNumberPolicy.
  ///
  /// In zh, this message translates to:
  /// **'阿里云号码认证服务隐私权政策'**
  String get privacyNumberPolicy;

  /// No description provided for @privacySmsPolicy.
  ///
  /// In zh, this message translates to:
  /// **'阿里云短信认证SDK隐私权政策（服务参考）'**
  String get privacySmsPolicy;

  /// No description provided for @privacyMobilePolicy.
  ///
  /// In zh, this message translates to:
  /// **'中国移动认证服务条款与隐私说明'**
  String get privacyMobilePolicy;

  /// No description provided for @privacyUnicomPolicy.
  ///
  /// In zh, this message translates to:
  /// **'中国联通认证隐私权政策'**
  String get privacyUnicomPolicy;

  /// No description provided for @privacyTelecomPolicy.
  ///
  /// In zh, this message translates to:
  /// **'天翼账号服务与隐私协议'**
  String get privacyTelecomPolicy;

  /// No description provided for @privacySharingTitle.
  ///
  /// In zh, this message translates to:
  /// **'四、共享、转让与公开披露'**
  String get privacySharingTitle;

  /// No description provided for @privacySharing.
  ///
  /// In zh, this message translates to:
  /// **'为完成您选择的认证，我们会向上述认证服务提供方及相应运营商提供必要信息；为提供云建模、文件存储和任务处理服务，相关文件会交由服务使用的对象存储和计算资源处理。处理范围以实现您请求的功能所必需为限。\n\n我们不会出售您的个人信息，或为第三方营销而共享。除获得您的授权、履行法定义务或法律法规允许的其他情形外，不会向无关第三方共享、转让或公开披露。若发生运营主体变更并涉及个人信息转移，将依法告知您并要求接收方继续履行相应保护义务。'**
  String get privacySharing;

  /// No description provided for @privacyStorageTitle.
  ///
  /// In zh, this message translates to:
  /// **'五、信息保存与安全保护'**
  String get privacyStorageTitle;

  /// No description provided for @privacyStorage.
  ///
  /// In zh, this message translates to:
  /// **'账号资料和业务数据保存在玄境云服务端，上传的图片保存在服务使用的对象存储中，工程模型由服务端保存。应用在本机安全保存会话和必要缓存；您可退出登录或清理缓存。\n\n我们仅在提供服务、处理您的请求和履行法定义务所需期限内保留相关信息。超出必要期限的信息将依法删除或匿名化。第三方认证服务的数据保存地点、期限和处理规则以其各自政策为准。本应用不会因号码认证主动将您的个人信息传输至境外。\n\n我们通过HTTPS传输、密码散列、账号访问控制、认证凭据时效与一次性校验等措施保护信息，限制未经授权的访问。任何系统都无法保证绝对安全；发生信息安全事件时，我们将依法采取处置措施，并履行必要的告知及报告义务。'**
  String get privacyStorage;

  /// No description provided for @privacyRightsTitle.
  ///
  /// In zh, this message translates to:
  /// **'六、如何管理您的信息'**
  String get privacyRightsTitle;

  /// No description provided for @privacyRights.
  ///
  /// In zh, this message translates to:
  /// **'您可在账户资料页查看和修改昵称、邮箱及单位；可在相应功能中管理自己上传的工程与模型，在缓存管理中清理本机缓存，或退出登录。您可以不勾选一键认证同意选项，或返回登录页选择其他方式；停止一键认证不影响此前已经依法进行的处理。\n\n如需查询、更正、复制、删除个人信息、撤回授权或申请注销账号，请联系baokx@seu.edu.cn。当前应用未提供自助账号注销入口，由运营团队核实身份后依法受理并回复。为保护账号安全，我们可能请求与申请相关的必要身份核验信息，不会要求提供登录密码或短信验证码。依法必须保留的信息或尚未完成的业务可能影响部分删除申请，我们会说明原因。'**
  String get privacyRights;

  /// No description provided for @privacyMinorsTitle.
  ///
  /// In zh, this message translates to:
  /// **'七、未成年人保护'**
  String get privacyMinorsTitle;

  /// No description provided for @privacyMinors.
  ///
  /// In zh, this message translates to:
  /// **'未成年人应在监护人指导下阅读本政策并使用相关服务。未满十四周岁的用户，应在取得监护人明确同意后使用涉及个人信息的功能。监护人如发现我们在未获适当授权的情况下处理了未成年人的信息，可通过隐私联系邮箱联系我们，我们将在核实后依法处理。'**
  String get privacyMinors;

  /// No description provided for @privacyUpdatesTitle.
  ///
  /// In zh, this message translates to:
  /// **'八、政策更新与联系我们'**
  String get privacyUpdatesTitle;

  /// No description provided for @privacyUpdates.
  ///
  /// In zh, this message translates to:
  /// **'我们可能随着功能或处理方式变化更新本政策，并在应用内展示最新版本。涉及处理目的、方式或个人信息种类等重大变化时，将依法进行提示并在必要时重新征求您的同意。\n\n如对本政策或个人信息处理有疑问、意见、投诉或权利请求，请联系：\n东南大学玄境创新团队\nbaokx@seu.edu.cn。'**
  String get privacyUpdates;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
