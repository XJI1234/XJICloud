import '../features/viewer/infrastructure/native3dgs_engine_adapter.dart';
import '../features/viewer/infrastructure/model_source_adapter.dart';
import '../features/viewer/presentation/viewer_controller.dart';
import '../features/viewer/domain/model_source.dart' as viewer;
import '../features/model_asset/application/download_model.dart';
import '../features/model_asset/domain/model_cache_port.dart';
import '../features/model_asset/domain/local_file_port.dart';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../features/identity/infrastructure/http_auth_repository.dart';
import '../features/identity/infrastructure/platform_fusion_auth.dart';
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
import 'completed_profile_access.dart';
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
  final businessSessions = CompletedProfileAccess(sessions);
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
  final cache = FileModelCache(files.cacheDirectory);
  final modelAssets = ModelAssetsController(
    HttpModelAssetRepository(api),
    files,
    modelCheckpoints,
    cache,
    businessSessions,
  );
  final training = UploadController(
    HttpTrainingJobRepository(api),
    AndroidImageSourceAdapter(snapshots),
    oss,
    datasetCheckpoints,
    SseJobEventAdapter(api),
    businessSessions,
    outputs: outputs,
  );
  final engine = Native3dgsEngineAdapter();
  final viewerController = ViewerController(
    ModelSourceAdapter(
      acquire: (source, cancellation, progress) async {
        await snapshots.ready();
        final account = businessSessions.session, generation = businessSessions.generation;
        if (account == null) return const Failure(DomainError('unauthorized'));
        bool current() =>
            sessions.generation == generation && !cancellation.isCancelled;
        switch (source) {
          case viewer.CloudModelSource(:final asset):
            final result = await DownloadModel(modelAssets.repository, cache)(
              account.userId,
              asset,
              generation,
              cancellation,
              current,
              onProgress: (b, t) => progress?.call(b, t),
            );
            if (result case Failure<CacheLease>(:final error)) {
              return Failure(error);
            }
            final lease = (result as Success<CacheLease>).value;
            try {
              final uri = await privateModelUri(await cache.resolve(lease));
              return Success(
                viewer.LocalModelRef(
                  handle: uri,
                  fileName: asset.fileName,
                  releaseResource: () async => lease.release(),
                ),
              );
            } catch (_) {
              lease.release();
              rethrow;
            }
          case viewer.LocalModelSource():
            final chosen = await files.choose(cancellation);
            if (chosen case Failure<LocalModelRef?>(:final error)) {
              return Failure(error);
            }
            final ref = (chosen as Success<LocalModelRef?>).value;
            if (ref == null) return const Failure(DomainError('cancelled'));
            Future<void> release() async {
              final result = await files.release(ref);
              if (result case Failure<void>(:final error)) throw error;
            }
            try {
              final uri = await privateModelUri(await files.resolveSource(ref));
              return Success(
                viewer.LocalModelRef(
                  handle: uri,
                  fileName: ref.fileName,
                  releaseResource: release,
                ),
              );
            } catch (_) {
              await release();
              rethrow;
            }
          case viewer.TrainingModelSource(:final output):
            final lease = await outputs.acquire(output, account.userId);
            try {
              final uri = await privateModelUri(lease.path);
              return Success(
                viewer.LocalModelRef(
                  handle: uri,
                  fileName: output.fileName,
                  releaseResource: lease.release,
                ),
              );
            } catch (_) {
              await lease.release();
              rethrow;
            }
        }
      },
    ),
    engine,
    businessSessions,
  );
  sessions.registerCleanup(() async {
    await viewerController.close();
    if (viewerController.session.error case final error?) throw error;
  });
  return AppContainer(
    initializeStorage: () async {
      try {
        await snapshots.ready();
        return const Success(null);
      } on DomainError catch (error) {
        return Failure(error);
      }
    },
    viewer: viewerController,
    viewerSurface: engine.surface,
    sessions: sessions,
    auth: HttpAuthRepository(api),
    fusion: PlatformFusionAuth(),
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
      businessSessions,
      stopProjectTransfers: (id) async {
        final result = await modelAssets.stopProjectTransfers(id);
        if (result is Failure<void>) return result;
        return training.stopProjectTransfers(id);
      },
    ),
  );
}
