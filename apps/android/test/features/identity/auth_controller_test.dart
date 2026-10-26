import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/presentation/auth_controller.dart';
import 'package:xjicloud_mobile/features/identity/domain/captcha_challenge.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

import 'auth_use_cases_test.dart' show FakeAuth;
import 'session_policy_test.dart' show session;

import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';

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
  test('register never activates token; failed captcha refreshes consumed challenge', () async {
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
    final oldKey = c.captcha!.key;
    repo.outcome = const Failure(DomainError('captcha'));
    await c.submit(
      username: 'alice',
      password: 'secret',
      confirmation: 'secret',
      captchaCode: 'wrong',
    );
    expect(c.error?.code, 'captcha');
    expect(c.captcha, isNotNull);
    expect(c.captcha!.key, isNot(oldKey));
    expect(activations, 0);
    repo.outcome = null;
    await c.submit(
      username: 'alice',
      password: 'secret',
      confirmation: 'secret',
      captchaCode: 'correct',
    );
    expect(c.registered, isTrue);
    expect(c.registration, isFalse);
    expect(activations, 0);
  });
}
