import '../../../shared/domain/result.dart';
import 'user_session.dart';
import 'captcha_challenge.dart';
import 'sms_challenge.dart';
import 'fusion_challenge.dart';
import 'account_profile.dart';

abstract interface class AuthRepository {
  Future<Result<UserSession>> login({
    required String username,
    required String password,
    String? captchaKey,
    String? captchaCode,
  });
  Future<Result<UserSession>> register({
    required String phone,
    required String password,
    required String smsChallengeId,
    required String smsCode,
  });
  Future<Result<SmsChallenge>> sendRegistrationSms({
    required String phone,
    required String captchaKey,
    required String captchaCode,
  });
  Future<Result<AccountProfile>> getProfile();
  Future<Result<AccountProfile>> updateProfile({
    required String displayName,
    required String email,
    required String organization,
  });
  Future<Result<FusionChallenge>> initializeFusion();
  Future<Result<UserSession>> loginWithFusion({
    required String challengeId,
    required String verifyToken,
  });
  Future<Result<CaptchaChallenge>> getCaptcha();
  Future<Result<bool>> needCaptcha(String username);
}
