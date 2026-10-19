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
  /// **'XJICloud'**
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
  /// **'欢迎使用 XJICloud'**
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
