import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/application/login.dart';
import 'package:xjicloud_mobile/features/identity/application/register.dart';
import 'package:xjicloud_mobile/features/identity/application/restore_session.dart';
import 'package:xjicloud_mobile/features/identity/domain/auth_repository.dart';
import 'package:xjicloud_mobile/features/identity/domain/captcha_challenge.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

import 'session_policy_test.dart' show MemoryStore, session;

class FakeAuth implements AuthRepository {
  int calls = 0;
  int captchaRequests = 0;
  String? lastUsername;
  Result<UserSession>? outcome;
  @override
  Future<Result<UserSession>> login({
    required String username,
    required String password,
    String? captchaKey,
    String? captchaCode,
  }) async {
    calls++;
    lastUsername = username;
    return outcome ?? Success(session());
  }

  @override
  Future<Result<UserSession>> register({
    required String username,
    required String password,
    String? displayName,
    required String captchaKey,
    required String captchaCode,
  }) => login(username: username, password: password);
  @override
  Future<Result<CaptchaChallenge>> getCaptcha() async =>
      Success(CaptchaChallenge(key: 'key${++captchaRequests}', imageBytes: [1]));
  @override
  Future<Result<bool>> needCaptcha(String username) async =>
      const Success(false);
}

void main() {
  test('registration requires challenge and confirmation before contacting backend', () async {
    final repo = FakeAuth();
    final useCase = Register(repo);
    expect(
      await useCase(
        username: 'alice',
        password: 'secret',
        confirmPassword: 'other',
        captchaKey: 'k',
        captchaCode: 'abc',
      ),
      isA<Failure<UserSession>>(),
    );
    expect(
      await useCase(
        username: 'alice',
        password: 'secret',
        confirmPassword: 'secret',
        captchaKey: '',
        captchaCode: '',
      ),
      isA<Failure<UserSession>>(),
    );
    expect(repo.calls, 0);
    expect(
      await useCase(
        username: 'alice',
        password: 'secret',
        confirmPassword: 'secret',
        captchaKey: 'k',
        captchaCode: 'abc',
      ),
      isA<Success<UserSession>>(),
    );
  });
  test('login preserves failure and trims only username', () async {
    final repo = FakeAuth()..outcome = const Failure(DomainError('network'));
    final result = await Login(repo)(username: ' alice ', password: 'secret');
    expect(repo.lastUsername, 'alice');
    expect((result as Failure).error.code, 'network');
  });
  test(
    'restoring expired session clears instead of extending expiresInMs',
    () async {
      final store = MemoryStore()..value = session();
      expect(
        await RestoreSession(store)(DateTime.utc(2030)),
        isA<Success<UserSession?>>(),
      );
      expect(store.value, isNull);
    },
  );
}
