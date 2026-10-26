import '../../../shared/infrastructure/api_client.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/user_session.dart';
import '../domain/captcha_challenge.dart';
import 'auth_mapper.dart';

final class HttpAuthRepository implements AuthRepository {
  const HttpAuthRepository(this.api);
  final ApiClient api;
  Future<Result<T>> _map<T>(
    Future<Result<Object?>> request,
    T Function(Map<String, dynamic>) mapper,
  ) async {
    final result = await request;
    if (result case Failure<Object?>(:final error)) return Failure(error);
    try {
      return Success(
        mapper((result as Success<Object?>).value as Map<String, dynamic>),
      );
    } on Object {
      return const Failure(DomainError('protocol'));
    }
  }

  @override
  Future<Result<CaptchaChallenge>> getCaptcha() =>
      _map(api.request('GET', '/api/v1/auth/captcha'), AuthMapper.captcha);
  @override
  Future<Result<bool>> needCaptcha(String username) => _map(
    api.request(
      'GET',
      '/api/v1/auth/need-captcha',
      query: {'username': username},
    ),
    (json) => json['needCaptcha'] as bool,
  );
  @override
  Future<Result<UserSession>> login({
    required String username,
    required String password,
    String? captchaKey,
    String? captchaCode,
  }) => _map(
    api.request(
      'POST',
      '/api/v1/auth/login',
      body: {
        'username': username,
        'password': password,
        'captchaKey': ?captchaKey,
        'captchaCode': ?captchaCode,
      },
    ),
    AuthMapper.session,
  );
  @override
  Future<Result<UserSession>> register({
    required String username,
    required String password,
    String? displayName,
    required String captchaKey,
    required String captchaCode,
  }) => _map(
    api.request(
      'POST',
      '/api/v1/auth/register',
      body: {
        'username': username,
        'password': password,
        'displayName': displayName,
        'captchaKey': captchaKey,
        'captchaCode': captchaCode,
      },
    ),
    AuthMapper.session,
  );
}
