import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/user_session.dart';

class Login {
  const Login(this.repository);
  final AuthRepository repository;
  Future<Result<UserSession>> call({
    required String username,
    required String password,
    String? captchaKey,
    String? captchaCode,
  }) {
    if (username.trim().length < 3 ||
        username.trim().length > 64 ||
        password.length < 6 ||
        password.length > 128) {
      return Future.value(const Failure(DomainError('validation')));
    }
    return repository.login(
      username: username.trim(),
      password: password,
      captchaKey: captchaKey,
      captchaCode: captchaCode,
    );
  }
}
