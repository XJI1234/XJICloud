import 'package:flutter/widgets.dart';

import '../features/viewer/presentation/viewer_controller.dart';
import '../features/identity/domain/auth_repository.dart';
import '../features/identity/domain/locale_store.dart';
import '../features/identity/domain/fusion_auth_port.dart';
import '../features/identity/presentation/profile_controller.dart';
import '../features/identity/presentation/auth_controller.dart';
import 'session_coordinator.dart';
import '../features/project/presentation/workspace_controller.dart';
import '../features/model_asset/presentation/model_assets_controller.dart';
import '../features/dataset_training/presentation/upload_controller.dart';
import '../shared/domain/result.dart';

/// Session-scoped capabilities, assembled only by create_container.dart.
final class AppContainer {
  AppContainer({
    required this.sessions,
    required this.auth,
    required this.locales,
    required this.closeTransport,
    this.workspace,
    this.viewer,
    this.viewerSurface,
    this.modelAssets,
    this.training,
    this.initializeStorage,
    this.fusion,
  }) {
    if (viewer != null) {
      sessions.addListener(viewer!.synchronize);
      viewer!.synchronize();
    }
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
  final FusionAuthPort? fusion;
  final LocaleStore locales;
  final void Function() closeTransport;
  final WorkspaceController? workspace;
  final ViewerController? viewer;
  final Widget Function()? viewerSurface;
  final ModelAssetsController? modelAssets;
  final UploadController? training;
  final Future<Result<void>> Function()? initializeStorage;
  AuthController createAuthController() =>
      AuthController(auth, activate: sessions.activate, fusion: fusion);
  ProfileController createProfileController() {
    final generation = sessions.generation;
    return ProfileController(auth, applyProfile: (profile) =>
        sessions.updateProfile(profile, expectedGeneration: generation));
  }
  void dispose() {
    if (viewer != null) {
      sessions.removeListener(viewer!.synchronize);
      viewer!.dispose();
    }
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
