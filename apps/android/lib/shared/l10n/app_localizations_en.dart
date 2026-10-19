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
      'Delete this project and all of its models, training tasks and datasets? This cannot be undone. Running server work may continue. Local active transfers will be stopped first.';

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

  @override
  String get models => 'Model assets';

  @override
  String get modelEmpty => 'No models in this project';

  @override
  String get modelUploadPolicy =>
      'PLY/SPZ, 1 byte–2 GiB. A private copy supports reliable resume.';

  @override
  String get modelValidation =>
      'Choose a valid PLY/SPZ file (1 byte–2 GiB). Free device storage if needed.';

  @override
  String get modelDownload => 'Download to private cache';

  @override
  String get modelCached => 'Cached';

  @override
  String get cacheRemove => 'Remove cached copy';

  @override
  String get modelCreated => 'Created';

  @override
  String get modelUpdated => 'Updated';

  @override
  String get deleteModel => 'Delete cloud model';

  @override
  String get deleteModelMessage =>
      'Delete this cloud model and invalidate its account cache? This cannot be undone.';

  @override
  String get pauseTransfer => 'Pause transfer';

  @override
  String get resumeTransfer => 'Check server and resume';

  @override
  String get reselectSource => 'Re-select the same source';

  @override
  String get abandonUpload => 'Cancel this upload';

  @override
  String get abandonUploadMessage =>
      'Stop local tracking and abort the upload session. An already completed cloud model remains; check the list.';

  @override
  String get uploadReady => 'Ready to upload';

  @override
  String get uploadPaused => 'Paused; check the server before resuming';

  @override
  String get uploadCompleting => 'Confirming completion';

  @override
  String get uploadUncertain => 'Completion needs reconciliation';

  @override
  String get uploadCompleted =>
      'Server acknowledged completion; check the refreshed model list';

  @override
  String get uploadPreparing => 'Preparing a private copy of your file';

  @override
  String get completionUncertain =>
      'The completion result is uncertain. Check the refreshed list, model ID and creation time. Uploads and same-name models are never chosen automatically.';

  @override
  String get uploadCreateUncertain =>
      'The upload-session response was lost. No automatic retry was made. Check project models; abandoned sessions expire on the server.';

  @override
  String get retryCompletion => 'Retry completion';

  @override
  String get retryCompletionMessage =>
      'Check the model list first. The current server can return “not found” for repeated completion. Retry only after checking; this creates no new upload.';

  @override
  String get reconcileModel => 'Confirm as this upload result';

  @override
  String get reconcileModelMessage =>
      'Check the model ID, filename, size and creation time before confirming this asset.';

  @override
  String get sourceChanged =>
      'The source is unavailable or changed. Re-select the exact same file to resume.';

  @override
  String get cacheTitle => 'Model cache';

  @override
  String get cacheDescription =>
      'Manage this account’s model downloads. Files in use remain. Source files and upload copies are preserved.';

  @override
  String get cacheClear => 'Clear unused cache';

  @override
  String get cacheFreed => 'Bytes released';

  @override
  String get cacheBytes => 'Cached bytes';

  @override
  String get cacheFull =>
      'Cache quota is exhausted or files are in use. Clear the cache or close models.';

  @override
  String get cacheInUse =>
      'This cache file is in use. Close the model and retry.';

  @override
  String get modelNotFound =>
      'The model or upload session was not found, deleted, or expired. Refresh and check the project list.';

  @override
  String get modelDeletedStorage =>
      'Cloud model deleted, but local cache cleanup failed. Retry clearing cache.';

  @override
  String get uploadCompletedStorage =>
      'Server acknowledged completion, but the local record could not be saved. Check the list and finish local tracking.';

  @override
  String get cancelledTransfer =>
      'Transfer paused or cancelled. Incomplete cache was not committed.';

  @override
  String get finishUpload => 'Finish local tracking';

  @override
  String get uploadDestination => 'Fixed destination project';

  @override
  String get cancelDownload => 'Cancel download';

  @override
  String get modelUnavailable =>
      'The selected model is unavailable. Refresh and check again.';

  @override
  String get sourceBusy =>
      'The system file picker is still open. Finish or cancel the previous selection.';

  @override
  String get chooseImages => 'Choose images';

  @override
  String get chooseImageFolder => 'Choose image folder';

  @override
  String get datasetPolicy =>
      'Choose 1–9999 JPG, PNG or WebP images. Originals upload sequentially without compression or conversion. Uploads pause in the background.';

  @override
  String get datasetName => 'Training job name';

  @override
  String get datasetPreview => 'Archive preview';

  @override
  String get datasetImageCount => 'Image count';

  @override
  String get datasetTotalBytes => 'Total upload bytes';

  @override
  String get datasetPaused =>
      'Local upload paused. The server job status is shown separately.';

  @override
  String get datasetResume => 'Check job and continue';

  @override
  String get datasetRestart => 'Cancel old job and resubmit';

  @override
  String get datasetRestartMessage =>
      'Upload URLs cannot be renewed. The app will check and cancel the old uploading job, then create a new job and reupload these sources. Re-select images if the sources are unavailable.';

  @override
  String get datasetRestartRequired =>
      'Upload URLs are unavailable after restart or expiry. Check the job first; if it is still uploading, confirm cancellation before submitting again.';

  @override
  String get datasetCreateUncertain =>
      'The create response is uncertain. No retry was made. Refresh jobs and check ID, name and creation time, then explicitly identify this job.';

  @override
  String get datasetValidation =>
      'Choose 1–9999 nonempty JPG, PNG or WebP images with matching names and MIME types. HEIC is unsupported.';

  @override
  String get datasetDiscard => 'Cancel upload and clear sources';

  @override
  String get datasetDiscardMessage =>
      'Stop local sends, check and cancel this server job, and clear unsubmitted sources. Identify the job first if creation is uncertain.';

  @override
  String get datasetLeave => 'Pause and leave';

  @override
  String get datasetLeaveMessage =>
      'Sources and confirmed objects remain. Nothing uploads in the background. Returning checks the job before resuming.';

  @override
  String get datasetConfirmCandidate => 'Identify as this created job';

  @override
  String get datasetCandidateMessage =>
      'Check the job ID, name and creation time. This links the record; it cannot infer uploaded objects or recover missing URLs.';

  @override
  String get datasetNoCandidate => 'I checked; this job was not created';

  @override
  String get datasetNoCandidateMessage =>
      'Continue only after checking the complete job list and confirming creation failed. This clears the create intent and retains sources for a manual new submission.';

  @override
  String get datasetCompleteRetry => 'Request queueing after checking';

  @override
  String get datasetCompleteMessage =>
      'Completion is not idempotent. Retry only when every image and manifest is acknowledged and a fresh query still shows UPLOADING. The app never retries automatically.';

  @override
  String get trainingJobs => 'Training jobs';

  @override
  String get noTrainingJobs => 'No training jobs in this project';

  @override
  String get jobId => 'Job ID';

  @override
  String get jobCancel => 'Cancel job';

  @override
  String get jobCancelMessage =>
      'Cancellation may not stop the worker immediately. The job can still complete; this page keeps reconciling its actual status.';

  @override
  String get jobDelete => 'Delete job record';

  @override
  String get jobDeleteMessage =>
      'Delete this terminal job record and its local download?';

  @override
  String get jobStateError => 'The job state changed. Refresh the job.';

  @override
  String get outputDownload => 'Download training output';

  @override
  String get outputDownloaded => 'Training output downloaded privately';

  @override
  String get outputUnavailable => 'The server has not provided an output URL';

  @override
  String get outputUrlExpired =>
      'The output URL expired. The server may keep returning the stored expired URL; querying again cannot guarantee renewal. Contact the server maintainer.';

  @override
  String get jobPending => 'Pending';

  @override
  String get jobUploading => 'Uploading';

  @override
  String get jobQueued => 'Queued';

  @override
  String get jobRunning => 'Running';

  @override
  String get jobCompleted => 'Completed';

  @override
  String get jobFailed => 'Failed';

  @override
  String get jobCancelled => 'Cancelled';
}
