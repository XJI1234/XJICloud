import '../../../shared/domain/result.dart';
import '../../../shared/domain/cancellation.dart';
import 'local_file_port.dart';
import 'model_asset.dart';

/// An opaque revision key identifies one account's private cached payload.
final class ModelCacheEntry {
  const ModelCacheEntry({
    required this.key,
    required this.modelId,
    required this.projectId,
    required this.fileName,
    required this.sizeBytes,
    required this.inUse,
  });
  final String key, modelId, projectId, fileName;
  final int sizeBytes;
  final bool inUse;
}

final class CacheLease {
  CacheLease(this.file, void Function() release) : _release = release;
  final LocalModelRef file;
  void Function()? _release;
  bool get isReleased => _release == null;
  void release() {
    _release?.call();
    _release = null;
  }
}

abstract interface class ModelCachePort {
  Future<Result<List<ModelCacheEntry>>> entries(String userId);

  /// Removes only a local revision, returning released bytes; active leases fail.
  Future<Result<int>> evict(String userId, String key);
  Future<Result<CacheLease?>> acquire(String userId, ModelAsset asset);
  Future<Result<CacheLease>> store(
    String userId,
    ModelAsset asset,
    Future<Result<void>> Function(Future<void> Function(Stream<List<int>>))
    download,
    Cancellation cancellation,
    bool Function() isCurrent, {
    void Function(int, int)? onProgress,
  });
  Future<Result<int>> clear(String userId);
  Future<Result<int>> size(String userId);
  Future<Result<void>> invalidate(String userId, String modelId);
  Future<Result<void>> retain(
    String userId,
    String projectId,
    List<ModelAsset> assets,
  );
}
