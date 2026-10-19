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
import '../shared/infrastructure/platform_file_snapshots.dart';
import '../shared/infrastructure/reconcile_private_sources.dart';
import '../shared/domain/result.dart';
import '../shared/domain/domain_error.dart';
import '../features/dataset_training/infrastructure/android_image_source_adapter.dart';
import '../features/dataset_training/infrastructure/http_training_job_repository.dart';
import '../features/dataset_training/infrastructure/oss_upload_adapter.dart';
import '../features/dataset_training/infrastructure/local_upload_checkpoint.dart';
import '../features/dataset_training/infrastructure/sse_job_event_adapter.dart';
import '../features/dataset_training/infrastructure/file_training_outputs.dart';
import '../features/dataset_training/presentation/upload_controller.dart';

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
  final snapshots = PlatformFileSnapshots();
  final modelCheckpoints = PreferencesUploadCheckpoints();
  final datasetCheckpoints = LocalUploadCheckpoint(
    () async => '${await snapshots.directory('training')}/checkpoints',
  );
  final oss = OssUploadAdapter();
  final outputs = FileTrainingOutputs(
    () => snapshots.directory('training'),
    oss.download,
    beforeUse: snapshots.ready,
  );
  snapshots.initialize = () async {
    await reconcilePrivateSources(await snapshots.directory('sources'), [
      modelCheckpoints.referencedSources,
      datasetCheckpoints.referencedSources,
    ]);
    await outputs.prepare();
  };
  final files = AndroidLocalFileAdapter(snapshots: snapshots);
  final modelAssets = ModelAssetsController(
    HttpModelAssetRepository(api),
    files,
    modelCheckpoints,
    FileModelCache(files.cacheDirectory),
    sessions,
  );
  final training = UploadController(
    HttpTrainingJobRepository(api),
    AndroidImageSourceAdapter(snapshots),
    oss,
    datasetCheckpoints,
    SseJobEventAdapter(api),
    sessions,
    outputs: outputs,
  );
  return AppContainer(
    initializeStorage: () async {
      try {
        await snapshots.ready();
        return const Success(null);
      } on DomainError catch (error) {
        return Failure(error);
      }
    },
    sessions: sessions,
    auth: HttpAuthRepository(api),
    locales: PreferencesLocaleStore(),
    closeTransport: () {
      api.close();
      oss.close();
    },
    modelAssets: modelAssets,
    training: training,
    workspace: WorkspaceController(
      HttpProjectRepository(api),
      LocalWorkspacePersistence(),
      sessions,
      stopProjectTransfers: (id) async {
        final result = await modelAssets.stopProjectTransfers(id);
        if (result is Failure<void>) return result;
        return training.stopProjectTransfers(id);
      },
    ),
  );
}
