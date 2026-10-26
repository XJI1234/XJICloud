import 'dart:convert';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/auth_repository.dart';
import '../domain/user_session.dart';

class Register {
  const Register(this.repository);
  final AuthRepository repository;
  Future<Result<UserSession>> call({
    required String phone,
    required String password,
    required String confirmPassword,
    required String smsChallengeId,
    required String smsCode,
  }) {
    if (!RegExp(r'^1[3-9][0-9]{9}$').hasMatch(phone.trim()) ||
        password.runes.length < 8 ||
        password.runes.length > 64 ||
        utf8.encode(password).length > 72 ||
        password != confirmPassword ||
        smsChallengeId.isEmpty ||
        !RegExp(r'^[0-9]{6}$').hasMatch(smsCode)) {
      return Future.value(
        const Failure(DomainError('registration_validation')),
      );
    }
    return repository.register(
      phone: phone.trim(),
      password: password,
      smsChallengeId: smsChallengeId,
      smsCode: smsCode,
    );
  }
}
