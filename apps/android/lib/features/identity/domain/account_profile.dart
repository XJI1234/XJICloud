final class AccountProfile {
  const AccountProfile({
    required this.userId,
    required this.username,
    required this.phone,
    required this.displayName,
    required this.email,
    required this.organization,
    required this.profileRequired,
  });
  final String userId, username, displayName, email, organization;
  final String? phone;
  final bool profileRequired;
}
