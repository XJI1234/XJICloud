import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/sms_challenge.dart';

final class SendRegistrationSms {
  const SendRegistrationSms(this.repository);
  final AuthRepository repository;
  Future<Result<SmsChallenge>> call({
    required String phone,
    required String captchaKey,
    required String captchaCode,
  }) {
    if (!RegExp(r'^1[3-9][0-9]{9}$').hasMatch(phone.trim()) ||
        captchaKey.isEmpty ||
        captchaCode.trim().isEmpty) {
      return Future.value(
        const Failure(DomainError('registration_validation')),
      );
    }
    return repository.sendRegistrationSms(
      phone: phone.trim(),
      captchaKey: captchaKey,
      captchaCode: captchaCode.trim(),
    );
  }
}
