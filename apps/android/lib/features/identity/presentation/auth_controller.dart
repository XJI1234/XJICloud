import 'package:flutter/foundation.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/captcha_challenge.dart';
import '../domain/user_session.dart';
import '../application/login.dart';
import '../application/register.dart';

final class AuthController extends ChangeNotifier {
  AuthController(this.repository, {required this.activate});
  final AuthRepository repository;
  final Future<Result<void>> Function(UserSession) activate;
  bool registration = false,
      submitting = false,
      captchaRequired = false,
      captchaLoading = false,
      registered = false;
  CaptchaChallenge? captcha;
  DomainError? error;
  int _epoch = 0, _lookup = 0, _challenge = 0;
  String _username = '';
  bool _disposed = false;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int epoch) => !_disposed && epoch == _epoch;
  void setRegistration(bool value) {
    ++_epoch;
    ++_lookup;
    ++_challenge;
    registration = value;
    captchaRequired = value;
    captcha = null;
    captchaLoading = false;
    error = null;
    registered = false;
    submitting = false;
    _notify();
    if (value) refreshCaptcha();
  }

  Future<void> checkUsername(String username) async {
    _username = username.trim();
    final ticket = ++_lookup;
    final epoch = _epoch;
    if (registration) return;
    ++_challenge;
    captchaLoading = false;
    captcha = null;
    captchaRequired = false;
    _notify();
    if (_username.length < 3) return;
    final result = await repository.needCaptcha(_username);
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

  Future<void> submit({
    required String username,
    required String password,
    String confirmation = '',
    String? displayName,
    String captchaCode = '',
  }) async {
    if (submitting || _disposed) return;
    submitting = true;
    error = null;
    registered = false;
    final epoch = _epoch;
    final isRegistration = registration;
    _notify();
    // Recheck at submission: a different client may have triggered captcha meanwhile.
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
        submitting = false;
        _notify();
        return;
      }
    }
    final usedCaptcha = captchaRequired;
    final Result<UserSession> result = isRegistration
        ? await Register(repository)(
            username: username,
            password: password,
            confirmPassword: confirmation,
            displayName: displayName,
            captchaKey: captcha?.key ?? '',
            captchaCode: captchaCode,
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
        if (isRegistration) {
          setRegistration(false);
          registered = true;
        } else {
          final saved = await activate(value);
          if (!_current(epoch)) return;
          if (saved case Failure<void>(error: final saveError)) {
            error = saveError;
          }
        }
      case Failure<UserSession>(:final error):
        this.error = error;
        if (usedCaptcha) {
          await refreshCaptcha();
        } else if (!isRegistration) {
          final need = await repository.needCaptcha(username.trim());
          if (!_current(epoch)) return;
          if (need case Success<bool>(value: true)) {
            captchaRequired = true;
            await refreshCaptcha();
          }
        }
    }
    if (!_current(epoch) && !registered) return;
    submitting = false;
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_epoch;
    super.dispose();
  }
}
