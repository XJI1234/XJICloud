import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/account_profile.dart';
import '../domain/auth_repository.dart';

final class SaveAccountProfile {
  const SaveAccountProfile(this.repository);
  final AuthRepository repository;
  Future<Result<AccountProfile>> call({
    required String displayName,
    required String email,
    required String organization,
  }) {
    final name = displayName.trim(),
        mail = email.trim(),
        org = organization.trim();
    if (name.isEmpty ||
        name.length > 128 ||
        mail.length > 254 ||
        !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(mail) ||
        org.isEmpty ||
        org.length > 200) {
      return Future.value(const Failure(DomainError('profile_validation')));
    }
    return repository.updateProfile(
      displayName: name,
      email: mail,
      organization: org,
    );
  }
}
