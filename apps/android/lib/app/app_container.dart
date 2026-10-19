import '../features/identity/domain/auth_repository.dart';
import '../features/identity/domain/locale_store.dart';
import '../features/identity/presentation/auth_controller.dart';
import 'session_coordinator.dart';
import '../features/project/presentation/workspace_controller.dart';

/// Session-scoped capabilities, assembled only by create_container.dart.
final class AppContainer {
  AppContainer({
    required this.sessions,
    required this.auth,
    required this.locales,
    required this.closeTransport,
    this.workspace,
  }) {
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
  AuthController createAuthController() =>
      AuthController(auth, activate: sessions.activate);
  void dispose() {
    if (workspace != null) {
      sessions.removeListener(workspace!.synchronize);
      workspace!.dispose();
    }
    sessions.dispose();
    closeTransport();
  }
}
