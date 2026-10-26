import '../../../shared/domain/result.dart';
import '../domain/session_store.dart';
import '../domain/user_session.dart';

class RestoreSession {
  const RestoreSession(this.store);
  final SessionStore store;
  Future<Result<UserSession?>> call(DateTime now) async {
    final result = await store.read();
    if (result case Success<UserSession?>(value: final session?)
        when session.isExpired(now)) {
      final cleared = await store.clear();
      if (cleared case Failure<void>(:final error)) return Failure(error);
      return const Success(null);
    }
    return result;
  }
}
