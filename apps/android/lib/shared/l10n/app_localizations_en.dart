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

  @override
  String get viewerOpenLocal => 'Open local PLY/SPZ';

  @override
  String get viewerChooseCloud => 'Browse cloud models';

  @override
  String get viewerOpen => 'View model';

  @override
  String get viewerTools => 'Viewer tools';

  @override
  String get viewerFixed => 'Fixed mode';

  @override
  String get viewerFree => 'Free mode';

  @override
  String get viewerFit => 'Fit';

  @override
  String get viewerReset => 'Reset';

  @override
  String get viewerFlip => 'Flip Y';

  @override
  String get viewerZoomIn => 'Zoom in';

  @override
  String get viewerZoomOut => 'Zoom out';

  @override
  String get viewerFullscreen => 'Fullscreen';

  @override
  String get viewerExitFullscreen => 'Exit fullscreen';

  @override
  String get viewerClose => 'Close model';

  @override
  String get viewerCancel => 'Cancel loading';

  @override
  String get viewerEmpty =>
      'Open a local file or choose a model from the current project.';

  @override
  String get viewerDownload => 'Download / prepare local file';

  @override
  String get viewerDecode => 'Decode';

  @override
  String get viewerUpload => 'GPU upload / await first frame';

  @override
  String get viewerFirstFrame => 'First frame ready';

  @override
  String get viewerRecover => 'Recovering canvas';

  @override
  String get viewerClosing => 'Closing model';

  @override
  String get viewerError =>
      'Model loading or rendering failed. Cancel or choose a valid PLY/SPZ; the existing scene is retained.';

  @override
  String get viewerUnsupported =>
      'Native viewer is unsupported. Requires Android 10+, arm64, Vulkan 1.1 and 4KB memory pages.';

  @override
  String get viewerConfigFit =>
      'No compatible camera metadata: automatically fitted without changing cloud configuration.';

  @override
  String get viewerForward => 'Forward';

  @override
  String get viewerBackward => 'Backward';

  @override
  String get viewerLeft => 'Move left';

  @override
  String get viewerRight => 'Move right';

  @override
  String get viewerUp => 'Move up';

  @override
  String get viewerDown => 'Move down';

  @override
  String get viewerOutput => 'Open training output';

  @override
  String get viewerOutputNote =>
      'Training output must be valid Gaussian PLY; completion does not guarantee format compatibility.';

  @override
  String get viewerCloudTitle => 'Cloud models';

  @override
  String get viewerLocalTitle => 'On this device';

  @override
  String get viewerRefresh => 'Refresh models';

  @override
  String get viewerProject => 'Project';

  @override
  String get fusionLogin => 'One-tap sign in / register';

  @override
  String get passwordLogin => 'Password sign in';

  @override
  String get phoneRegistration => 'Register with a phone number';

  @override
  String get privacyConsent =>
      'I agree to phone authentication and have read the';

  @override
  String get fusionPrivacyPurpose =>
      'Sign in or register with this device\'s number. With your consent, phone, network and device data verify the number. Review the operator agreement to continue.';

  @override
  String get fusionPrivacyPolicy => 'Privacy policy';

  @override
  String get privacyRequired =>
      'Read the data use notice and agree before using one-tap authentication.';

  @override
  String get fusionUnavailable =>
      'One-tap authentication is unavailable on this device or service. Continue with phone registration or password sign in.';

  @override
  String get fusionFailed =>
      'Phone authentication did not finish. Continue with phone registration or try again.';

  @override
  String get fusionBackendFailed =>
      'The authentication service could not complete sign-in. Use phone registration or try again later.';

  @override
  String get phoneNumber => 'Mainland China phone number';

  @override
  String get phoneValidation =>
      'Enter an 11-digit mainland China phone number.';

  @override
  String get sendSms => 'Send SMS code';

  @override
  String get resendSms => 'Resend SMS code';

  @override
  String get smsCode => '6-digit SMS code';

  @override
  String get smsValidation => 'Enter the 6-digit SMS code.';

  @override
  String get smsRequired => 'Verify your phone with an SMS code first.';

  @override
  String get registrationPasswordValidation =>
      'Use 8–64 characters, at most 72 UTF-8 bytes.';

  @override
  String get registrationValidation =>
      'Check the phone number, SMS code and password confirmation.';

  @override
  String get completeProfileTitle => 'Complete your profile';

  @override
  String get profileIntro =>
      'Add your display name, email and organization to use cloud services.';

  @override
  String get profileEmail => 'Email';

  @override
  String get profileOrganization => 'Organization';

  @override
  String get saveProfile => 'Save profile';

  @override
  String get editProfile => 'Account profile';

  @override
  String get profileValidation =>
      'Enter a valid display name, email and organization.';

  @override
  String get profileRequiredError => 'Complete your profile to continue.';

  @override
  String get phoneRegistered =>
      'This phone number is already registered. Sign in with your password.';

  @override
  String get phoneUnavailable => 'No phone number detected';

  @override
  String get policyUnavailable =>
      'The privacy policy cannot be opened. Please try again later.';

  @override
  String get privacyEffectiveDate =>
      'Updated: 27 October 2026\nEffective: 27 October 2026';

  @override
  String get privacyIntroduction =>
      'XJICloud (玄境云) is operated by 东南大学玄境创新团队. This policy explains how account authentication and cloud modeling use personal information and how you can manage it. Please read the phone number, uploaded files and third-party authentication disclosures before using these features.\n\nOperator: 东南大学玄境创新团队\nPrivacy contact: baokx@seu.edu.cn';

  @override
  String get privacyCollectionTitle => '1. Information we collect and use';

  @override
  String get privacyCollection =>
      'Registration and sign in: SMS registration processes the phone number, image challenge, SMS code and password you submit to verify the number and create an account. Passwords are stored as irreversible hashes on the server. One-tap authentication uses only a number verified by the authentication service to sign in or create the same account.\n\nProfile: Your nickname, email and organization form your account profile. Saving an email does not verify ownership of that address.\n\nCloud modeling and viewing: When you select, upload or open files, the app processes the selected photos, datasets, PLY/SPZ models, file names, sizes and task information to upload, train, view and download them. Upload only information you are authorized to use. System file selection does not grant access to all files.\n\nSession and security: The device securely stores the sign-in credential and necessary account data. The server processes request IP addresses, authentication state and necessary operation records for rate limiting, abuse prevention and fault diagnosis. Codes and authentication credentials are not used for advertising.';

  @override
  String get privacyChoiceTitle => '2. Authentication choices and permissions';

  @override
  String get privacyChoice =>
      'One-tap authentication is optional. Only after you actively agree to this policy and choose one-tap sign in do we check SIM availability and start the authentication process. The fusion SDK is not initialized before consent. The operator screen separately displays its agreement for you to review and accept. Reading this policy does not grant consent.\n\nIf no number is detected, authentication is unavailable or you choose another method, use image and SMS verification to register, or sign in with your existing password. Declining one-tap authentication does not prevent other available methods.\n\nNumber authentication uses Android network access and network-state permissions, and may switch between Wi-Fi and cellular networks to verify the number. The package also includes the number SDK\'s READ_PHONE_STATE declaration for authentication-related SIM/operator state. If Android asks for permission, you may decline and choose another sign-in method. The app does not read SMS messages, contacts or call history for authentication, and does not request SMS-reading permission. Enter the code manually. File access is authorized through the system picker when you use the corresponding feature.';

  @override
  String get privacySdkTitle => '3. Authentication providers and data';

  @override
  String get privacySdk =>
      'We use Alibaba Cloud fusion and number authentication, provided by Alibaba Cloud Computing (Beijing) Co., Ltd.\n\nAlibaba Cloud Fusion Authentication SDK (integrated version 1.2.16): fusion verification uses device name, model and operating-system version as disclosed by the provider. This app enables the operator number-authentication path.\n\nNumber Authentication SDK/service: one-tap sign in and verification process phone number, IP address, network type and state, manufacturer, device model, operating system, SIM state and operator type; Wi-Fi and cellular channels may be switched when needed. Only a number verified by our server is used for your account.\n\nThe number SDK integrates three operators:\n• China Mobile / 中移互联网有限公司: number identification, sign in and security controls use network type and address, operator type, device phone number, SIM state, device type, operating system and manufacturer.\n• China Unicom / 中国联合网络通信集团有限公司: number identification, sign in and security controls use network type and address, operator type, device phone number, device type, operating system and manufacturer.\n• Tianyi Account / 天翼数字生活科技有限公司: number identification, sign in and security controls use registered and device phone numbers, network connection type, state and address, operator type, SIM state, device type, operating system and manufacturer. The operator corresponding to your current number handles the authentication; review its authorization-screen agreement.\n\nManual SMS registration uses Alibaba Cloud server APIs to send and verify codes, processing your submitted number and verification information. This app does not enable the fusion SDK SMS, graphic-verification or Umeng risk nodes. XJICloud serves its own image challenge. Read the official policies below and the operator agreements for provider details.';

  @override
  String get privacyNumberPolicy =>
      'Alibaba Cloud number authentication privacy policy';

  @override
  String get privacySmsPolicy =>
      'Alibaba Cloud SMS authentication SDK policy (service reference)';

  @override
  String get privacyMobilePolicy =>
      'China Mobile authentication terms and privacy notice';

  @override
  String get privacyUnicomPolicy =>
      'China Unicom authentication privacy policy';

  @override
  String get privacyTelecomPolicy =>
      'Tianyi Account service and privacy agreement';

  @override
  String get privacySharingTitle => '4. Sharing, transfers and disclosure';

  @override
  String get privacySharing =>
      'Necessary information is supplied to the authentication provider and relevant operator to perform your chosen authentication. Uploaded files are processed by the object storage and computing resources used to provide cloud modeling and storage. Processing is limited to what your requested feature needs.\n\nWe do not sell personal information or share it for third-party marketing. Sharing, transfers or public disclosure otherwise require your authorization, a legal obligation or another basis permitted by law. If an operator change involves personal-information transfer, we will provide the required notice and require the recipient to continue the applicable protections.';

  @override
  String get privacyStorageTitle => '5. Retention and protection';

  @override
  String get privacyStorage =>
      'Account and business data are held on XJICloud servers; uploaded images use the service\'s object storage and project models are held by the server. The app securely stores its session and necessary local caches. You can sign out or clear caches.\n\nInformation is retained only for providing the service, handling your requests and meeting legal obligations, then deleted or anonymized as required. Provider retention periods, locations and handling rules are set out in their policies. This app does not initiate overseas transfer of personal information for number authentication.\n\nHTTPS, password hashing, account access controls and time-limited, single-use authentication checks help protect data. No system can guarantee absolute security. We will respond to security incidents and provide notifications and reports as required by law.';

  @override
  String get privacyRightsTitle => '6. Managing your information';

  @override
  String get privacyRights =>
      'View and edit your nickname, email and organization in your account profile. Manage your own projects and models in their respective features, clear local caches in cache management, or sign out. Leave one-tap consent unchecked or return to choose another method to stop using one-tap authentication. Withdrawal does not affect processing already lawfully performed.\n\nFor access, correction, copying, deletion, withdrawal or account closure, email baokx@seu.edu.cn. The app currently has no self-service account-deletion screen. The operator will verify identity and handle requests under applicable law. We may request necessary proof related to the request, but never your password or SMS code. Required retention or unfinished business may limit deletion; we will explain the reason.';

  @override
  String get privacyMinorsTitle => '7. Children and minors';

  @override
  String get privacyMinors =>
      'Minors should read this policy and use the service with a guardian\'s guidance. Users under fourteen should obtain explicit guardian consent before using features involving personal information. Guardians who believe information was processed without appropriate authorization may contact our privacy email; we will verify and handle the matter under applicable law.';

  @override
  String get privacyUpdatesTitle => '8. Updates and contact';

  @override
  String get privacyUpdates =>
      'This policy may change as features or processing change. The latest version is available in the app. Material changes to purposes, methods or information categories will be notified as required, with renewed consent where necessary.\n\nFor questions, complaints or personal-information requests, contact:\n东南大学玄境创新团队\nbaokx@seu.edu.cn.';
}
