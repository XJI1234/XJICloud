import '../domain/domain_error.dart';
import '../l10n/app_localizations.dart';

String errorText(AppLocalizations l, DomainError error) => switch (error.code) {
  'network' => l.errorNetwork,
  'credentials' => l.errorCredentials,
  'captcha' => l.errorCaptcha,
  'captcha_required' => l.errorCaptchaRequired,
  'username_taken' => l.errorUsernameTaken,
  'rate_limit' => l.errorRateLimit,
  'storage' || 'session_invalid' || 'cleanup' => l.errorStorage,
  'expired' || 'unauthorized' => l.errorExpired,
  'forbidden' => l.errorForbidden,
  'protocol' || 'redirect' => l.errorProtocol,
  'preferences' => l.errorPreferences,
  'workspace_storage' => l.workspaceStorage,
  'project_deleted_storage' => l.projectDeletedStorage,
  'project_unavailable' => l.projectUnavailable,
  'project_validation' => l.projectValidation,
  _ => l.errorRequest,
};
