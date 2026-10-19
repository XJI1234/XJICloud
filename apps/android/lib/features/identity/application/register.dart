import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/user_session.dart';

class Register {
  const Register(this.repository);
  final AuthRepository repository;
  Future<Result<UserSession>> call({
    required String username,
    required String password,
    required String confirmPassword,
    String? displayName,
    required String captchaKey,
    required String captchaCode,
  }) {
    if (username.trim().length < 3 ||
        username.trim().length > 64 ||
        password.length < 6 ||
        password.length > 128 ||
        password != confirmPassword ||
        (displayName?.length ?? 0) > 128 ||
        captchaKey.isEmpty ||
        captchaCode.trim().isEmpty) {
      return Future.value(const Failure(DomainError('validation')));
    }
    return repository.register(
      username: username.trim(),
      password: password,
      displayName: displayName?.trim(),
      captchaKey: captchaKey,
      captchaCode: captchaCode.trim(),
    );
  }
}
