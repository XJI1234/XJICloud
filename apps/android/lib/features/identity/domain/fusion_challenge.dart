final class FusionChallenge {
  const FusionChallenge({
    required this.challengeId,
    required this.authToken,
    required this.schemeCode,
    required this.templateId,
    required this.expiresInSeconds,
  });
  final String challengeId;
  final String authToken;
  final String schemeCode;
  final String templateId;
  final int expiresInSeconds;
}
