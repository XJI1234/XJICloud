import '../features/identity/domain/auth_repository.dart';
import '../features/identity/domain/locale_store.dart';
import '../features/identity/presentation/auth_controller.dart';
import 'session_coordinator.dart';

/// Actual Identity capabilities only; later BCs extend this typed container.
final class AppContainer {
  AppContainer({
    required this.sessions,
    required this.auth,
    required this.locales,
    required this.closeTransport,
  });
  final SessionCoordinator sessions;
  final AuthRepository auth;
  final LocaleStore locales;
  final void Function() closeTransport;
  AuthController createAuthController() =>
      AuthController(auth, activate: sessions.activate);
  void dispose() {
    sessions.dispose();
    closeTransport();
  }
}
