final class CaptchaChallenge {
  CaptchaChallenge({required this.key, required List<int> imageBytes})
    : imageBytes = List.unmodifiable(imageBytes);
  final String key;
  final List<int> imageBytes;
}
