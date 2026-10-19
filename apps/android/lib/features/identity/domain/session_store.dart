import '../../../shared/domain/result.dart';
import 'user_session.dart';

abstract interface class SessionStore {
  Future<Result<UserSession?>> read();
  Future<Result<void>> write(UserSession session);
  Future<Result<void>> clear();
}
