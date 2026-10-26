import '../../../shared/domain/result.dart';
import '../../../shared/domain/cancellation.dart';

final class LocalModelRef {
  const LocalModelRef({
    required this.id,
    required this.fileName,
    required this.sizeBytes,
    required this.fingerprint,
  });
  final String id, fileName, fingerprint;
  final int sizeBytes;
  bool sameSource(LocalModelRef other) =>
      fileName == other.fileName &&
      sizeBytes == other.sizeBytes &&
      fingerprint == other.fingerprint;
}

abstract interface class LocalFilePort {
  Future<Result<LocalModelRef?>> choose(Cancellation cancellation);
  Future<Result<void>> verify(LocalModelRef source);
  Stream<List<int>> read(LocalModelRef source, int start, int endExclusive);
  Future<Result<void>> release(LocalModelRef source);
}
