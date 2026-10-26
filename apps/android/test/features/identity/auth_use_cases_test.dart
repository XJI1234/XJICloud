import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/application/login.dart';
import 'package:xjicloud_mobile/features/identity/application/register.dart';
import 'package:xjicloud_mobile/features/identity/application/send_registration_sms.dart';
import 'package:xjicloud_mobile/features/identity/application/restore_session.dart';
import 'package:xjicloud_mobile/features/identity/domain/account_profile.dart';
import 'package:xjicloud_mobile/features/identity/domain/auth_repository.dart';
import 'package:xjicloud_mobile/features/identity/domain/captcha_challenge.dart';
import 'package:xjicloud_mobile/features/identity/domain/fusion_challenge.dart';
import 'package:xjicloud_mobile/features/identity/domain/sms_challenge.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

import 'session_policy_test.dart' show MemoryStore, session;

class FakeAuth implements AuthRepository {
  int calls = 0, captchaRequests = 0, smsRequests = 0, fusionRequests = 0;
  String? lastUsername, lastPhone, lastSmsCode, lastSmsChallenge;
  Result<UserSession>? outcome;
  Result<SmsChallenge> smsOutcome = const Success(
    SmsChallenge(
      challengeId: 'sms-id',
      expiresInSeconds: 300,
      retryAfterSeconds: 60,
    ),
  );
  Result<FusionChallenge> fusionOutcome = const Success(
    FusionChallenge(
      challengeId: 'fusion-id',
      authToken: 'sdk-token',
      schemeCode: 'scheme',
      templateId: '100001',
      expiresInSeconds: 900,
    ),
  );
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
    required String phone,
    required String password,
    required String smsChallengeId,
    required String smsCode,
  }) async {
    calls++;
    lastPhone = phone;
    lastSmsCode = smsCode;
    lastSmsChallenge = smsChallengeId;
    return outcome ?? Success(session());
  }

  @override
  Future<Result<SmsChallenge>> sendRegistrationSms({
    required String phone,
    required String captchaKey,
    required String captchaCode,
  }) async {
    smsRequests++;
    lastPhone = phone;
    return smsOutcome;
  }

  @override
  Future<Result<FusionChallenge>> initializeFusion() async {
    fusionRequests++;
    return fusionOutcome;
  }

  @override
  Future<Result<UserSession>> loginWithFusion({
    required String challengeId,
    required String verifyToken,
  }) async {
    calls++;
    return outcome ?? Success(session());
  }

  @override
  Future<Result<AccountProfile>> getProfile() async => const Success(
    AccountProfile(
      userId: 'alice',
      username: 'alice',
      displayName: 'Alice',
      phone: null,
      email: 'a@example.test',
      organization: 'Example',
      profileRequired: false,
    ),
  );
  @override
  Future<Result<AccountProfile>> updateProfile({
    required String displayName,
    required String email,
    required String organization,
  }) => getProfile();
  @override
  Future<Result<CaptchaChallenge>> getCaptcha() async => Success(
    CaptchaChallenge(key: 'key${++captchaRequests}', imageBytes: [1]),
  );
  @override
  Future<Result<bool>> needCaptcha(String username) async =>
      const Success(false);
}

void main() {
  test('phone registration validates SMS challenge, confirmation and bcrypt UTF8 limit before HTTP', () async {
    final repo = FakeAuth();
    final register = Register(repo);
    for (final password in ['short', '密' * 25, 'a' * 65]) {
      expect(
        await register(
          phone: '13800138000',
          password: password,
          confirmPassword: password,
          smsChallengeId: 'sms',
          smsCode: '012345',
        ),
        isA<Failure<UserSession>>(),
      );
    }
    for (final code in ['', '12345', '1234567', 'abcdef']) {
      expect(
        await register(
          phone: '13800138000',
          password: 'password',
          confirmPassword: 'password',
          smsChallengeId: 'sms',
          smsCode: code,
        ),
        isA<Failure<UserSession>>(),
      );
    }
    expect(
      await register(
        phone: '+8613800138000',
        password: 'password',
        confirmPassword: 'password',
        smsChallengeId: 'sms',
        smsCode: '012345',
      ),
      isA<Failure<UserSession>>(),
    );
    expect(
      await register(
        phone: '13800138000',
        password: 'password',
        confirmPassword: 'different',
        smsChallengeId: 'sms',
        smsCode: '012345',
      ),
      isA<Failure<UserSession>>(),
    );
    expect(
      await register(
        phone: '13800138000',
        password: 'password',
        confirmPassword: 'password',
        smsChallengeId: '',
        smsCode: '012345',
      ),
      isA<Failure<UserSession>>(),
    );
    expect(repo.calls, 0);
    expect(
      await register(
        phone: ' 13800138000 ',
        password: '密' * 24,
        confirmPassword: '密' * 24,
        smsChallengeId: 'sms',
        smsCode: '012345',
      ),
      isA<Success<UserSession>>(),
    );
    expect(repo.lastPhone, '13800138000');
    expect(repo.lastSmsCode, '012345');
    expect(repo.lastSmsChallenge, 'sms');
  });
  test(
    'SMS sending needs a mainland phone and solved image challenge',
    () async {
      final repo = FakeAuth();
      final send = SendRegistrationSms(repo);
      expect(
        await send(phone: 'alice', captchaKey: 'k', captchaCode: '1234'),
        isA<Failure<SmsChallenge>>(),
      );
      expect(
        await send(phone: '13800138000', captchaKey: '', captchaCode: '1234'),
        isA<Failure<SmsChallenge>>(),
      );
      expect(repo.smsRequests, 0);
      expect(
        await send(
          phone: ' 13800138000 ',
          captchaKey: 'k',
          captchaCode: '1234',
        ),
        isA<Success<SmsChallenge>>(),
      );
      expect(repo.lastPhone, '13800138000');
    },
  );
  test('password login preserves failure and trims only username', () async {
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
