import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/captcha_challenge.dart';
import '../domain/fusion_auth_port.dart';
import '../domain/fusion_challenge.dart';
import '../domain/sms_challenge.dart';
import '../domain/user_session.dart';
import '../domain/privacy_document.dart';
import '../application/login.dart';
import '../application/register.dart';
import '../application/send_registration_sms.dart';

enum AuthMode { fusion, password, registration }

final class AuthController extends ChangeNotifier {
  AuthController(this.repository, {required this.activate, this.fusion});
  final AuthRepository repository;
  final Future<Result<void>> Function(UserSession) activate;
  final FusionAuthPort? fusion;
  AuthMode mode = AuthMode.fusion;
  bool get registration => mode == AuthMode.registration;
  bool submitting = false,
      captchaRequired = false,
      captchaLoading = false,
      registered = false,
      privacyAccepted = false;
  CaptchaChallenge? captcha;
  SmsChallenge? smsChallenge;
  DomainError? error;
  int retryAfterSeconds = 0;
  int smsRevision = 0;
  int _epoch = 0, _lookup = 0, _challenge = 0;
  String _phone = '';
  bool _disposed = false;
  Timer? _cooldown;
  int _retryDelay = 0;
  bool _current(int epoch) => !_disposed && epoch == _epoch;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _resetMode(AuthMode value) {
    if (_disposed) return;
    ++_epoch;
    ++_lookup;
    ++_challenge;
    unawaited(fusion?.cancel());
    _cooldown?.cancel();
    mode = value;
    captchaRequired = value == AuthMode.registration;
    captcha = null;
    smsChallenge = null;
    retryAfterSeconds = 0;
    smsRevision++;
    captchaLoading = submitting = registered = false;
    error = null;
    _notify();
  }

  void setRegistration(bool value) {
    _resetMode(value ? AuthMode.registration : AuthMode.password);
    if (value && !_disposed) unawaited(refreshCaptcha());
  }

  void setPasswordLogin() => setRegistration(false);
  void setFusionLogin() => _resetMode(AuthMode.fusion);
  Future<Result<void>> openPrivacyDocument(PrivacyDocument document) async =>
      await fusion?.openPrivacyDocument(document) ??
      const Failure(DomainError('policy_unavailable'));

  void setPrivacyAccepted(bool value) {
    if (_disposed) return;
    privacyAccepted = value;
    if (!value && submitting && mode == AuthMode.fusion) {
      _resetMode(AuthMode.fusion);
    }
    _notify();
  }

  void changePhone(String phone) {
    if (_disposed || _phone == phone.trim()) return;
    final wasBusy = submitting;
    _phone = phone.trim();
    ++_epoch;
    ++_challenge;
    _cooldown?.cancel();
    smsChallenge = null;
    smsRevision++;
    retryAfterSeconds = 0;
    submitting = false;
    captchaRequired = registration;
    error = null;
    if (wasBusy || captcha == null) unawaited(refreshCaptcha());
    _notify();
  }

  Future<void> checkUsername(String username) async {
    final name = username.trim();
    final ticket = ++_lookup;
    final epoch = _epoch;
    if (registration) return;
    ++_challenge;
    captchaLoading = false;
    captcha = null;
    captchaRequired = false;
    _notify();
    if (name.length < 3) return;
    final result = await repository.needCaptcha(name);
    if (!_current(epoch) || ticket != _lookup) return;
    switch (result) {
      case Success<bool>(:final value):
        captchaRequired = value;
        if (value) await refreshCaptcha();
      case Failure<bool>(:final error):
        this.error = error;
    }
    _notify();
  }

  Future<void> refreshCaptcha() async {
    if (_disposed || registration && smsChallenge != null && !captchaRequired) {
      return;
    }
    final epoch = _epoch;
    final ticket = ++_challenge;
    captcha = null;
    captchaLoading = true;
    _notify();
    final result = await repository.getCaptcha();
    if (!_current(epoch) || ticket != _challenge) return;
    captchaLoading = false;
    switch (result) {
      case Success<CaptchaChallenge>(:final value):
        captcha = value;
      case Failure<CaptchaChallenge>(:final error):
        this.error = error;
    }
    _notify();
  }

  Future<void> requestSmsResend() async {
    if (_disposed || submitting || retryAfterSeconds > 0) return;
    captchaRequired = true;
    await refreshCaptcha();
  }

  Future<void> sendSms({
    required String phone,
    required String captchaCode,
  }) async {
    if (_disposed || submitting || !registration || retryAfterSeconds > 0) {
      return;
    }
    if (_phone != phone.trim()) {
      // Initial submission may follow a programmatic field fill without onChanged.
      _phone = phone.trim();
      smsChallenge = null;
    }
    final epoch = _epoch;
    final resultFuture = SendRegistrationSms(repository)(
      phone: phone,
      captchaKey: captcha?.key ?? '',
      captchaCode: captchaCode,
    );
    submitting = true;
    error = null;
    _notify();
    final result = await resultFuture;
    if (!_current(epoch)) return;
    submitting = false;
    switch (result) {
      case Success<SmsChallenge>(:final value):
        smsChallenge = value;
        smsRevision++;
        captcha = null;
        captchaRequired = false;
        captchaLoading = false;
        ++_challenge;
        _startCooldown(value.retryAfterSeconds);
      case Failure<SmsChallenge>(:final error):
        this.error = error;
        await refreshCaptcha();
    }
    _notify();
  }

  void _startCooldown(int seconds) {
    _cooldown?.cancel();
    _retryDelay = retryAfterSeconds = seconds;
    if (seconds == 0) return;
    _cooldown = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_disposed) return;
      // Timer.tick counts elapsed periods, including periods whose callbacks were delayed.
      retryAfterSeconds = (_retryDelay - timer.tick).clamp(0, _retryDelay);
      if (retryAfterSeconds == 0) _cooldown?.cancel();
      _notify();
    });
  }

  Future<void> startFusion() async {
    if (_disposed || submitting) return;
    if (!privacyAccepted) {
      error = const DomainError('privacy_required');
      _notify();
      return;
    }
    if (fusion == null) {
      _fallback(const DomainError('fusion_unavailable'));
      return;
    }
    final epoch = _epoch;
    submitting = true;
    error = null;
    _notify();
    bool released = false;
    bool enteredSdk = false;
    try {
      final availability = await fusion!.canAuthenticate();
      if (!_current(epoch)) return;
      if (availability case Failure<bool>(:final error)) {
        _fallback(error);
        return;
      }
      if (!(availability as Success<bool>).value) {
        _fallback(const DomainError('phone_unavailable'));
        return;
      }
      final initialized = await repository.initializeFusion();
      if (!_current(epoch)) return;
      if (initialized case Failure<FusionChallenge>(:final error)) {
        _fallback(error);
        return;
      }
      enteredSdk = true;
      final verified = await fusion!.authenticate(
        (initialized as Success<FusionChallenge>).value,
      );
      if (!_current(epoch)) return;
      if (verified case Failure<String>(:final error)) {
        if (error.code == 'cancelled') {
          this.error = null;
        } else {
          _fallback(error);
        }
        return;
      }
      final session = await repository.loginWithFusion(
        challengeId: initialized.value.challengeId,
        verifyToken: (verified as Success<String>).value,
      );
      if (!_current(epoch)) return;
      if (session case Failure<UserSession>(:final error)) {
        _fallback(error);
        return;
      }
      // Activating the session replaces/disposes this route. Finish native
      // verification first so route cleanup cannot turn backend PASS into cancel.
      await fusion!.complete(true);
      released = true;
      if (!_current(epoch)) return;
      final saved = await activate((session as Success<UserSession>).value);
      if (!_current(epoch)) return;
      if (saved case Failure<void>(:final error)) {
        this.error = error;
        return;
      }
    } on Object {
      if (_current(epoch)) _fallback(const DomainError('fusion_failed'));
    } finally {
      // A mode switch/dispose already cancels the old native session. A stale
      // completion must not close an authentication started in the new mode.
      if (enteredSdk && !released && _current(epoch)) {
        await fusion!.complete(false);
      }
      if (_current(epoch)) {
        submitting = false;
        _notify();
      }
    }
  }

  void _fallback(DomainError reason) {
    // Preserve the operation epoch until its finally releases the native SDK.
    mode = AuthMode.registration;
    captchaRequired = true;
    captcha = null;
    error = reason.code == 'server'
        ? const DomainError('fusion_backend')
        : reason;
    _notify();
    unawaited(refreshCaptcha());
  }

  Future<void> submit({
    String username = '',
    String phone = '',
    required String password,
    String confirmation = '',
    String? displayName,
    String captchaCode = '',
    String smsCode = '',
  }) async {
    if (submitting || _disposed) return;
    submitting = true;
    error = null;
    registered = false;
    final epoch = _epoch;
    final isRegistration = registration;
    _notify();
    if (!isRegistration) {
      ++_lookup;
      final need = await repository.needCaptcha(username.trim());
      if (!_current(epoch)) return;
      if (need case Failure<bool>(:final error)) {
        this.error = error;
        submitting = false;
        _notify();
        return;
      }
      captchaRequired = (need as Success<bool>).value;
      if (captchaRequired && (captcha == null || captchaCode.trim().isEmpty)) {
        error = const DomainError('captcha_required');
        await refreshCaptcha();
        if (!_current(epoch)) return;
        submitting = false;
        _notify();
        return;
      }
    }
    if (isRegistration && (_phone != phone.trim() || smsChallenge == null)) {
      error = const DomainError('sms_required');
      submitting = false;
      _notify();
      return;
    }
    final usedCaptcha = captchaRequired;
    final result = isRegistration
        ? await Register(repository)(
            phone: phone,
            password: password,
            confirmPassword: confirmation,
            smsChallengeId: smsChallenge!.challengeId,
            smsCode: smsCode,
          )
        : await Login(repository)(
            username: username,
            password: password,
            captchaKey: usedCaptcha ? captcha?.key : null,
            captchaCode: usedCaptcha ? captchaCode.trim() : null,
          );
    if (!_current(epoch)) return;
    switch (result) {
      case Success<UserSession>(:final value):
        if (isRegistration) _cooldown?.cancel();
        final saved = await activate(value);
        if (!_current(epoch)) return;
        if (saved case Failure<void>(:final error)) {
          this.error = error;
        } else {
          registered = isRegistration;
        }
      case Failure<UserSession>(:final error):
        this.error = error;
        if (!isRegistration) {
          if (usedCaptcha) {
            await refreshCaptcha();
          } else {
            final need = await repository.needCaptcha(username.trim());
            if (!_current(epoch)) return;
            if (need case Success<bool>(value: true)) {
              captchaRequired = true;
              await refreshCaptcha();
            }
          }
        }
    }
    if (!_current(epoch)) return;
    submitting = false;
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_epoch;
    _cooldown?.cancel();
    unawaited(fusion?.cancel());
    super.dispose();
  }
}
