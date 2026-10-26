import '../../../shared/domain/result.dart';
import 'user_session.dart';
import 'captcha_challenge.dart';

abstract interface class AuthRepository {
  Future<Result<UserSession>> login({
    required String username,
    required String password,
    String? captchaKey,
    String? captchaCode,
  });
  Future<Result<UserSession>> register({
    required String username,
    required String password,
    String? displayName,
    required String captchaKey,
    required String captchaCode,
  });
  Future<Result<CaptchaChallenge>> getCaptcha();
  Future<Result<bool>> needCaptcha(String username);
}
