final class SmsChallenge {
  const SmsChallenge({
    required this.challengeId,
    required this.expiresInSeconds,
    required this.retryAfterSeconds,
  });
  final String challengeId;
  final int expiresInSeconds;
  final int retryAfterSeconds;
}
