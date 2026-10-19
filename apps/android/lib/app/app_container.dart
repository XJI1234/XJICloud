import '../features/identity/domain/auth_repository.dart';
import '../features/identity/domain/locale_store.dart';
import '../features/identity/presentation/auth_controller.dart';
import 'session_coordinator.dart';
import '../features/project/presentation/workspace_controller.dart';
import '../features/model_asset/presentation/model_assets_controller.dart';
import '../features/dataset_training/presentation/upload_controller.dart';

/// Session-scoped capabilities, assembled only by create_container.dart.
final class AppContainer {
  AppContainer({
    required this.sessions,
    required this.auth,
    required this.locales,
    required this.closeTransport,
    this.workspace,
    this.modelAssets,
    this.training,
  }) {
    if (training != null) {
      sessions.addListener(training!.synchronize);
      training!.synchronize();
    }
    if (modelAssets != null) {
      sessions.addListener(modelAssets!.synchronize);
      modelAssets!.synchronize();
    }
    if (workspace != null) {
      sessions.addListener(workspace!.synchronize);
      workspace!.synchronize();
    }
  }
  final SessionCoordinator sessions;
  final AuthRepository auth;
  final LocaleStore locales;
  final void Function() closeTransport;
  final WorkspaceController? workspace;
  final ModelAssetsController? modelAssets;
  final UploadController? training;
  AuthController createAuthController() =>
      AuthController(auth, activate: sessions.activate);
  void dispose() {
    if (training != null) {
      sessions.removeListener(training!.synchronize);
      training!.dispose();
    }
    if (modelAssets != null) {
      sessions.removeListener(modelAssets!.synchronize);
      modelAssets!.dispose();
    }
    if (workspace != null) {
      sessions.removeListener(workspace!.synchronize);
      workspace!.dispose();
    }
    sessions.dispose();
    closeTransport();
  }
}
