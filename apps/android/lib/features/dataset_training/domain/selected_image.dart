import '../../../shared/domain/result.dart';
import '../../../shared/domain/cancellation.dart';

/// Opaque durable snapshot identity; never a content URI or filesystem path.
final class SelectedImage {
  const SelectedImage({
    required this.sourceId,
    required this.name,
    required this.relativeName,
    required this.contentType,
    required this.sizeBytes,
    this.fingerprint = '',
  });
  final String sourceId, name, relativeName, contentType, fingerprint;
  final int sizeBytes;
}

abstract interface class ImageSourcePort {
  Future<Result<List<SelectedImage>?>> choose(
    bool directory,
    Cancellation cancellation,
  );
  Future<Result<void>> verify(SelectedImage source);
  Stream<List<int>> read(SelectedImage source);
  Future<Result<void>> release(SelectedImage source);
}
