import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../features/identity/infrastructure/http_auth_repository.dart';
import '../features/identity/infrastructure/secure_session_store.dart';
import '../features/identity/infrastructure/preferences_locale_store.dart';
import '../shared/infrastructure/api_client.dart';
import '../shared/infrastructure/sdk_licenses.dart';
import '../features/project/infrastructure/http_project_repository.dart';
import '../features/project/infrastructure/local_workspace_persistence.dart';
import '../features/project/presentation/workspace_controller.dart';
import '../features/model_asset/infrastructure/android_local_file_adapter.dart';
import '../features/model_asset/infrastructure/file_model_cache.dart';
import '../features/model_asset/infrastructure/http_model_asset_repository.dart';
import '../features/model_asset/infrastructure/preferences_upload_checkpoints.dart';
import '../features/model_asset/presentation/model_assets_controller.dart';
import 'app_container.dart';
import 'session_coordinator.dart';

/// The sole infrastructure assembly point. No production default API is assumed.
AppContainer createContainer({
  String apiOrigin = const String.fromEnvironment('API_BASE_URL'),
}) {
  final sessions = SessionCoordinator(
    SecureSessionStore(const FlutterSecureStorage()),
  );
  final api = ApiClient(
    origin: apiOrigin,
    sessions: sessions,
    allowDebugHttp: kDebugMode,
  );
  registerSdkLicenses();
  final files = AndroidLocalFileAdapter();
  final modelAssets = ModelAssetsController(
    HttpModelAssetRepository(api),
    files,
    PreferencesUploadCheckpoints(),
    FileModelCache(files.cacheDirectory),
    sessions,
  );
  return AppContainer(
    sessions: sessions,
    auth: HttpAuthRepository(api),
    locales: PreferencesLocaleStore(),
    closeTransport: api.close,
    modelAssets: modelAssets,
    workspace: WorkspaceController(
      HttpProjectRepository(api),
      LocalWorkspacePersistence(),
      sessions,
      stopProjectTransfers: modelAssets.stopProjectTransfers,
    ),
  );
}
