import 'account_profile.dart';

final class UserSession {
  UserSession({
    required this.userId,
    required this.username,
    required this.displayName,
    required this.token,
    required DateTime expiresAt,
    this.profileRequired = false,
  }) : expiresAt = expiresAt.toUtc();
  final String userId;
  final String username;
  final String displayName;
  final String token;
  final DateTime expiresAt;
  final bool profileRequired;
  bool isExpired(DateTime now) => !now.toUtc().isBefore(expiresAt);

  UserSession withProfile(AccountProfile profile) {
    if (profile.userId != userId || profile.username != username) {
      throw ArgumentError('Profile does not match the current identity');
    }
    return UserSession(
      userId: userId,
      username: username,
      displayName: profile.displayName,
      token: token,
      expiresAt: expiresAt,
      profileRequired: profile.profileRequired,
    );
  }
}
