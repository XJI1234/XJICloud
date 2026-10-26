import '../../../shared/infrastructure/api_client.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/user_session.dart';
import '../domain/captcha_challenge.dart';
import '../domain/account_profile.dart';
import '../domain/sms_challenge.dart';
import '../domain/fusion_challenge.dart';
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
    required String phone,
    required String password,
    required String smsChallengeId,
    required String smsCode,
  }) => _map(
    api.request(
      'POST',
      '/api/v1/auth/register',
      body: {
        'phone': phone,
        'password': password,
        'smsChallengeId': smsChallengeId,
        'smsCode': smsCode,
      },
    ),
    AuthMapper.session,
  );

  @override
  Future<Result<SmsChallenge>> sendRegistrationSms({
    required String phone,
    required String captchaKey,
    required String captchaCode,
  }) => _map(
    api.request(
      'POST',
      '/api/v1/auth/registration-sms',
      body: {
        'phone': phone,
        'captchaKey': captchaKey,
        'captchaCode': captchaCode,
      },
    ),
    (json) {
      final id = json['challengeId'] as String;
      final expiry = json['expiresInSeconds'] as int;
      final retry = json['retryAfterSeconds'] as int;
      if (id.isEmpty || expiry <= 0 || retry < 0) {
        throw const FormatException('Invalid SMS challenge');
      }
      return SmsChallenge(
        challengeId: id,
        expiresInSeconds: expiry,
        retryAfterSeconds: retry,
      );
    },
  );

  @override
  Future<Result<FusionChallenge>> initializeFusion() =>
      _map(api.request('POST', '/api/v1/auth/fusion/init'), (json) {
        final id = json['challengeId'] as String;
        final token = json['authToken'] as String;
        final scheme = json['schemeCode'] as String;
        final template = json['templateId'] as String;
        final expiry = json['expiresInSeconds'] as int;
        if ([id, token, scheme, template].any((s) => s.trim().isEmpty) ||
            expiry <= 0) {
          throw const FormatException('Invalid fusion challenge');
        }
        return FusionChallenge(
          challengeId: id,
          authToken: token,
          schemeCode: scheme,
          templateId: template,
          expiresInSeconds: expiry,
        );
      });

  @override
  Future<Result<UserSession>> loginWithFusion({
    required String challengeId,
    required String verifyToken,
  }) => _map(
    api.request(
      'POST',
      '/api/v1/auth/fusion/login',
      body: {'challengeId': challengeId, 'verifyToken': verifyToken},
    ),
    AuthMapper.session,
  );

  @override
  Future<Result<AccountProfile>> getProfile() => _map(
    api.request('GET', '/api/v1/account', authenticated: true),
    AuthMapper.profile,
  );

  @override
  Future<Result<AccountProfile>> updateProfile({
    required String displayName,
    required String email,
    required String organization,
  }) => _map(
    api.request(
      'PUT',
      '/api/v1/account',
      authenticated: true,
      body: {
        'displayName': displayName,
        'email': email,
        'organization': organization,
      },
    ),
    AuthMapper.profile,
  );
}
