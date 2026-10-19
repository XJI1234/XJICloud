import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/model_asset.dart';
import '../domain/model_asset_repository.dart';
import '../domain/model_cache_port.dart';

final class DownloadModel {
  DownloadModel(this.repository, this.cache);
  final ModelAssetRepository repository;
  final ModelCachePort cache;
  Future<Result<CacheLease>> call(
    String userId,
    ModelAsset asset,
    int generation,
    Cancellation cancellation,
    bool Function() isCurrent, {
    TransferProgress? onProgress,
  }) async {
    final hit = await cache.acquire(userId, asset);
    if (!isCurrent() || cancellation.isCancelled) {
      if (hit case Success<CacheLease?>(value: final lease?)) lease.release();
      return const Failure(DomainError('cancelled'));
    }
    if (hit case Failure<CacheLease?>(:final error)) return Failure(error);
    if (hit case Success<CacheLease?>(value: final lease?)) {
      return Success(lease);
    }
    return cache.store(
      userId,
      asset,
      (consume) => repository.downloadToFile(
        asset.id,
        generation,
        consume,
        cancellation,
      ),
      cancellation,
      isCurrent,
      onProgress: onProgress,
    );
  }
}
