// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'XJICloud';

  @override
  String get home => 'Home';

  @override
  String get projects => 'Projects';

  @override
  String get viewer => 'Model viewer';

  @override
  String get account => 'Account';

  @override
  String get upload => 'Upload';

  @override
  String get uploadImages => 'Upload images and train';

  @override
  String get uploadModel => 'Upload PLY/SPZ model';

  @override
  String get loading => 'Working';

  @override
  String get cancel => 'Cancel';

  @override
  String get confirm => 'Confirm';

  @override
  String get welcome => 'Welcome to XJICloud';

  @override
  String get login => 'Sign in';

  @override
  String get register => 'Create account';

  @override
  String get username => 'Username';

  @override
  String get password => 'Password';

  @override
  String get confirmPassword => 'Confirm password';

  @override
  String get displayName => 'Display name (optional)';

  @override
  String get captchaCode => 'Verification code';

  @override
  String get refreshCaptcha => 'Refresh verification image';

  @override
  String get showPassword => 'Show password';

  @override
  String get hidePassword => 'Hide password';

  @override
  String get switchRegister => 'New here? Create an account';

  @override
  String get switchLogin => 'Already registered? Sign in';

  @override
  String get registrationSuccess => 'Account created. Please sign in.';

  @override
  String get language => 'Language';

  @override
  String get toggleLanguage => '中文';

  @override
  String get help => 'Help and about';

  @override
  String get helpContent =>
      'XJICloud manages and views 3D Gaussian models. Account details come from your sign-in session. Sign-in and registration require a server connection. Sign in again when your session expires.';

  @override
  String get logout => 'Sign out';

  @override
  String get sessionExpiry => 'Session expires';

  @override
  String get userId => 'Account ID';

  @override
  String get retry => 'Retry';

  @override
  String get close => 'Close';

  @override
  String get requiredField => 'This field is required.';

  @override
  String get usernameValidation => 'Use 3–64 characters for your username.';

  @override
  String get passwordValidation => 'Use 6–128 characters for your password.';

  @override
  String get confirmationValidation => 'Passwords do not match.';

  @override
  String get displayNameValidation =>
      'Display name must be at most 128 characters.';

  @override
  String get errorNetwork => 'Unable to connect. Please try again.';

  @override
  String get errorCredentials => 'Incorrect username or password.';

  @override
  String get errorCaptcha =>
      'The code is incorrect or expired. Enter the new code.';

  @override
  String get errorCaptchaRequired => 'Enter the image verification code.';

  @override
  String get errorUsernameTaken => 'This username is already taken.';

  @override
  String get errorRateLimit => 'Too many requests. Try again later.';

  @override
  String get errorStorage =>
      'Unable to securely read or clear your session. Retry; if this continues, clear app data in system settings.';

  @override
  String get errorExpired => 'Your session expired. Please sign in again.';

  @override
  String get errorRequest => 'The request failed. Please try again.';

  @override
  String get errorForbidden =>
      'Access denied. Sign in again or contact your administrator.';

  @override
  String get errorProtocol => 'Unexpected server response. Try again later.';

  @override
  String get errorPreferences => 'Unable to save language. Please try again.';

  @override
  String get configurationError => 'Invalid service address configuration';

  @override
  String get viewLicenses => 'Open source licenses';

  @override
  String get sdkAbout => 'Native3DGS SDK 0.2.2 · XJICloud for Android';

  @override
  String get currentProject => 'Current project';

  @override
  String get recentProjects => 'Recently opened';

  @override
  String get createProject => 'Create project';

  @override
  String get firstProject => 'Create your first project';

  @override
  String get projectName => 'Project name';

  @override
  String get projectDescription => 'Description';

  @override
  String get saveProject => 'Save';

  @override
  String get editProject => 'Edit project';

  @override
  String get deleteProject => 'Delete project';

  @override
  String get setCurrentProject => 'Set as current';

  @override
  String get projectCreated => 'Created';

  @override
  String get projectEmpty => 'No projects yet';

  @override
  String get selectProject => 'Select a project to view its details.';

  @override
  String get projectUnavailable =>
      'This project is unavailable. Refresh your projects.';

  @override
  String get projectValidation =>
      'Enter a name of 1–256 characters and a description of at most 2000 characters.';

  @override
  String get workspaceStorage =>
      'Unable to save or load your workspace. Please retry.';

  @override
  String get projectDeletedStorage =>
      'Project deleted. Local workspace could not be saved; refresh to reconcile.';

  @override
  String get deleteProjectMessage =>
      'Delete this project and all of its models, training tasks and datasets? This cannot be undone. Running server work may continue.';

  @override
  String get discardChanges => 'Discard changes?';

  @override
  String get discardChangesMessage => 'Your changes have not been saved.';

  @override
  String get keepEditing => 'Keep editing';

  @override
  String get discard => 'Discard';

  @override
  String get projectsIntro => 'Organize your models and training in projects.';

  @override
  String get noRecentProjects => 'Projects you open will appear here.';

  @override
  String get noCurrentProject => 'Choose a project to continue.';

  @override
  String get openProject => 'Open project';
}
