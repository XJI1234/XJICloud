import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/presentation/auth_controller.dart';
import 'package:xjicloud_mobile/features/identity/domain/captcha_challenge.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

import 'auth_use_cases_test.dart' show FakeAuth;
import 'session_policy_test.dart' show session;

import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/features/identity/domain/fusion_auth_port.dart';
import 'package:xjicloud_mobile/features/identity/domain/privacy_document.dart';
import 'package:xjicloud_mobile/features/identity/domain/fusion_challenge.dart';
import 'package:xjicloud_mobile/features/identity/domain/sms_challenge.dart';

class FakeFusion implements FusionAuthPort {
  bool available = true;
  int policyOpens = 0;
  @override
  Future<Result<bool>> canAuthenticate() async => Success(available);
  @override
  Future<Result<void>> openPrivacyDocument(PrivacyDocument document) async {
    policyOpens++;
    return const Success(null);
  }

  int authentications = 0, cancellations = 0;
  final completed = <bool>[];
  Completer<void>? completionGate;
  Completer<Result<String>>? pending;
  Result<String> outcome = const Success('verified-token');
  @override
  Future<Result<String>> authenticate(FusionChallenge challenge) async {
    authentications++;
    return pending == null ? outcome : await pending!.future;
  }

  @override
  Future<void> complete(bool success) async {
    completed.add(success);
    await completionGate?.future;
  }

  @override
  Future<void> cancel() async {
    cancellations++;
  }
}

class SlowSmsAuth extends FakeAuth {
  final pending = Completer<Result<SmsChallenge>>();
  @override
  Future<Result<SmsChallenge>> sendRegistrationSms({
    required String phone,
    required String captchaKey,
    required String captchaCode,
  }) => pending.future;
}

class SlowChallengeAuth extends FakeAuth {
  final challenge = Completer<Result<CaptchaChallenge>>();
  @override
  Future<Result<bool>> needCaptcha(String username) async =>
      Success(username == 'alice');
  @override
  Future<Result<CaptchaChallenge>> getCaptcha() => challenge.future;
}

class SlowLoginAuth extends FakeAuth {
  final response = Completer<Result<UserSession>>();
  @override
  Future<Result<UserSession>> login({
    required String username,
    required String password,
    String? captchaKey,
    String? captchaCode,
  }) {
    calls++;
    return response.future;
  }
}

class RacingAuth extends FakeAuth {
  final pending = <String, Completer<Result<bool>>>{};
  int challenges = 0;
  @override
  Future<Result<bool>> needCaptcha(String username) =>
      (pending[username] ??= Completer()).future;
  @override
  Future<Result<CaptchaChallenge>> getCaptcha() async =>
      Success(CaptchaChallenge(key: 'key${++challenges}', imageBytes: [1]));
}

void main() {
  test(
    'no SIM falls back before requesting server credentials or starting SDK',
    () async {
      final repo = FakeAuth();
      final fusion = FakeFusion()..available = false;
      final c = AuthController(
        repo,
        fusion: fusion,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      c.setPrivacyAccepted(true);
      await c.startFusion();
      expect(repo.fusionRequests, 0);
      expect(fusion.authentications, 0);
      expect(c.registration, isTrue);
      expect(c.error?.code, 'phone_unavailable');
      expect(repo.captchaRequests, 1);
    },
  );
  test('failed authentication keeps submissions blocked until native release finishes', () async {
    final repo = FakeAuth()..outcome = const Failure(DomainError('server'));
    final fusion = FakeFusion()..completionGate = Completer<void>();
    final c = AuthController(
      repo,
      fusion: fusion,
      activate: (_) async => const Success(null),
    );
    addTearDown(c.dispose);
    c.setPrivacyAccepted(true);
    final pending = c.startFusion();
    await Future<void>.delayed(Duration.zero);
    expect(fusion.completed, [false]);
    expect(c.submitting, isTrue);
    await c.startFusion();
    expect(fusion.authentications, 1);
    fusion.completionGate!.complete();
    await pending;
    expect(c.submitting, isFalse);
  });
  test(
    'backend PASS releases SDK before session activation disposes login',
    () async {
      final fusion = FakeFusion();
      late AuthController c;
      c = AuthController(
        FakeAuth(),
        fusion: fusion,
        activate: (_) async {
          expect(fusion.completed, [true]);
          c.dispose();
          return const Success(null);
        },
      );
      c.setPrivacyAccepted(true);
      await c.startFusion();
      expect(fusion.completed, [true]);
    },
  );
  test(
    'old image fetch cannot repopulate captcha after username changes',
    () async {
      final repo = SlowChallengeAuth();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      final old = c.checkUsername('alice');
      await Future<void>.delayed(Duration.zero);
      await c.checkUsername('bob');
      repo.challenge.complete(
        Success(CaptchaChallenge(key: 'old', imageBytes: [1])),
      );
      await old;
      expect(c.captchaRequired, isFalse);
      expect(c.captcha, isNull);
      expect(c.captchaLoading, isFalse);
    },
  );
  test('disposed pending login never activates; duplicate submit does not send twice', () async {
    final repo = SlowLoginAuth();
    int activated = 0;
    final c = AuthController(
      repo,
      activate: (_) async {
        activated++;
        return const Success(null);
      },
    );
    final pending = c.submit(username: 'alice', password: 'secret');
    await Future<void>.delayed(Duration.zero);
    await c.submit(username: 'alice', password: 'secret');
    expect(repo.calls, 1);
    c.dispose();
    repo.response.complete(Success(session()));
    await pending;
    expect(activated, 0);
  });
  test('late need-captcha for previous username or mode never wins', () async {
    final repo = RacingAuth();
    final c = AuthController(repo, activate: (_) async => const Success(null));
    addTearDown(c.dispose);
    final first = c.checkUsername('alice');
    final second = c.checkUsername('bob');
    repo.pending['bob']!.complete(const Success(false));
    await second;
    repo.pending['alice']!.complete(const Success(true));
    await first;
    expect(c.captchaRequired, isFalse);
    expect(c.captcha, isNull);
    final third = c.checkUsername('carol');
    c.setRegistration(true);
    await Future<void>.delayed(Duration.zero);
    repo.pending['carol']!.complete(const Success(false));
    await third;
    expect(c.captchaRequired, isTrue);
    expect(c.captcha, isNotNull);
  });
  test('phone registration activates its returned session without password re-login', () async {
    final repo = FakeAuth();
    int activations = 0;
    final c = AuthController(
      repo,
      activate: (_) async {
        activations++;
        return const Success(null);
      },
    );
    addTearDown(c.dispose);
    c.setRegistration(true);
    await Future<void>.delayed(Duration.zero);
    await c.sendSms(phone: '13800138000', captchaCode: '1234');
    await c.submit(
      phone: '13800138000',
      password: 'password',
      confirmation: 'password',
      smsCode: '012345',
    );
    expect(c.registered, isTrue);
    expect(activations, 1);
    expect(repo.calls, 1);
    expect(repo.lastSmsChallenge, 'sms-id');
  });
  testWidgets(
    'SMS cooldown never refreshes consumed captcha until active resend',
    (tester) async {
      final repo = FakeAuth();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      c.setRegistration(true);
      await tester.pump();
      await c.sendSms(phone: '13800138000', captchaCode: '1234');
      expect(c.captcha, isNull);
      expect(c.captchaRequired, isFalse);
      expect(c.retryAfterSeconds, 60);
      await c.sendSms(phone: '13800138000', captchaCode: '1234');
      expect(repo.smsRequests, 1);
      await tester.pump(const Duration(seconds: 61));
      expect(c.retryAfterSeconds, 0);
      expect(c.captcha, isNull);
      expect(repo.captchaRequests, 1);
      await c.requestSmsResend();
      expect(c.captcha, isNotNull);
      expect(repo.captchaRequests, 2);
    },
  );
  test('changing phone discards pending SMS and prevents old challenge registration', () async {
    final repo = SlowSmsAuth();
    final c = AuthController(repo, activate: (_) async => const Success(null));
    addTearDown(c.dispose);
    c.setRegistration(true);
    await Future<void>.delayed(Duration.zero);
    final pending = c.sendSms(phone: '13800138000', captchaCode: '1234');
    c.changePhone('13900139000');
    repo.pending.complete(
      const Success(
        SmsChallenge(
          challengeId: 'old',
          expiresInSeconds: 300,
          retryAfterSeconds: 60,
        ),
      ),
    );
    await pending;
    expect(c.smsChallenge, isNull);
    await c.submit(
      phone: '13900139000',
      password: 'password',
      confirmation: 'password',
      smsCode: '012345',
    );
    expect(repo.calls, 0);
  });
  test(
    'fusion requires explicit privacy consent before any SDK or init request',
    () async {
      final repo = FakeAuth();
      final fusion = FakeFusion();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
        fusion: fusion,
      );
      addTearDown(c.dispose);
      await c.startFusion();
      expect(repo.fusionRequests, 0);
      expect(fusion.authentications, 0);
      expect(c.error?.code, 'privacy_required');
      c.setPrivacyAccepted(true);
      await c.startFusion();
      expect(repo.fusionRequests, 1);
      expect(fusion.completed, [true]);
    },
  );
  test('fusion unavailable falls back with reason; user cancellation stays on login', () async {
    final repo = FakeAuth();
    final fusion = FakeFusion()
      ..outcome = const Failure(DomainError('fusion_unavailable'));
    final c = AuthController(
      repo,
      activate: (_) async => const Success(null),
      fusion: fusion,
    );
    addTearDown(c.dispose);
    c.setPrivacyAccepted(true);
    await c.startFusion();
    expect(c.registration, isTrue);
    expect(c.error?.code, 'fusion_unavailable');
    expect(fusion.completed, [false]);
    c.setFusionLogin();
    fusion.outcome = const Failure(DomainError('cancelled'));
    await c.startFusion();
    expect(c.registration, isFalse);
    expect(c.submitting, isFalse);
    expect(fusion.completed, [false, false]);
  });
  test(
    'fusion backend failure releases SDK with false and explains fallback',
    () async {
      final repo = FakeAuth()..outcome = const Failure(DomainError('server'));
      final fusion = FakeFusion();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
        fusion: fusion,
      );
      addTearDown(c.dispose);
      c.setPrivacyAccepted(true);
      await c.startFusion();
      expect(fusion.completed, [false]);
      expect(c.registration, isTrue);
      expect(c.error?.code, 'fusion_backend');
    },
  );
  test(
    'mode switch cancels SDK and ignores late successful authentication',
    () async {
      final repo = FakeAuth();
      final fusion = FakeFusion()..pending = Completer<Result<String>>();
      var activations = 0;
      final c = AuthController(
        repo,
        activate: (_) async {
          activations++;
          return const Success(null);
        },
        fusion: fusion,
      );
      c.setPrivacyAccepted(true);
      final pending = c.startFusion();
      await Future<void>.delayed(Duration.zero);
      c.setRegistration(true);
      fusion.pending!.complete(const Success('late'));
      await pending;
      expect(fusion.cancellations, greaterThan(0));
      expect(repo.calls, 0);
      expect(activations, 0);
      expect(
        fusion.completed,
        isEmpty,
        reason: 'cancel already released old SDK; late complete must not close a new session',
      );
      c.dispose();
    },
  );
  test(
    'dispose cancels pending native authentication and discards late token',
    () async {
      final repo = FakeAuth();
      final fusion = FakeFusion()..pending = Completer<Result<String>>();
      var activations = 0;
      final c = AuthController(
        repo,
        activate: (_) async {
          activations++;
          return const Success(null);
        },
        fusion: fusion,
      );
      c.setPrivacyAccepted(true);
      final pending = c.startFusion();
      await Future<void>.delayed(Duration.zero);
      c.dispose();
      fusion.pending!.complete(const Success('late'));
      await pending;
      expect(fusion.cancellations, 1);
      expect(repo.calls, 0);
      expect(activations, 0);
    },
  );
}
