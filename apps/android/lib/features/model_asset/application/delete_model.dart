import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../domain/model_asset_repository.dart';
import '../domain/model_cache_port.dart';

final class DeleteModel {
  DeleteModel(this.repository, this.cache);
  final ModelAssetRepository repository;
  final ModelCachePort cache;
  Future<Result<void>> call(
    String userId,
    String modelId,
    int generation,
    bool Function() isCurrent,
  ) async {
    if (!isCurrent()) return const Failure(DomainError('cancelled'));
    final result = await repository.delete(modelId, generation);
    if (result is Failure<void>) return result;
    final invalidated = await cache.invalidate(userId, modelId);
    if (invalidated is Failure<void>) {
      return const Failure(DomainError('model_deleted_storage'));
    }
    return const Success(null);
  }
}
