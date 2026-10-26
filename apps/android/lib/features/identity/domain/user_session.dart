final class UserSession {
  UserSession({
    required this.userId,
    required this.username,
    required this.displayName,
    required this.token,
    required DateTime expiresAt,
  }) : expiresAt = expiresAt.toUtc();
  final String userId;
  final String username;
  final String displayName;
  final String token;
  final DateTime expiresAt;
  bool isExpired(DateTime now) => !now.toUtc().isBefore(expiresAt);
}
