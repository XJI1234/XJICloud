import '../../../shared/domain/result.dart';
import '../../../shared/domain/cancellation.dart';

abstract interface class ObjectStoragePort {
  Future<Result<void>> put(
    String url,
    String contentType,
    int length,
    Stream<List<int>> bytes,
    Cancellation cancellation,
    void Function(int) onProgress,
  );
}
