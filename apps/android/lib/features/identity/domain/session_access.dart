import '../../../shared/domain/result.dart';
import 'user_session.dart';

/// Credential snapshots bind requests to the session that actually sent them.
abstract interface class SessionAccess {
  UserSession? get session;
  int get generation;
  Future<Result<void>> invalidate({int? expectedGeneration});
}
