import '../../../shared/domain/result.dart';
import '../domain/model_cache_port.dart';

final class ManageCache {
  ManageCache(this.cache);
  final ModelCachePort cache;
  Future<Result<List<ModelCacheEntry>>> entries(String userId) =>
      cache.entries(userId);
  Future<Result<int>> evict(String userId, String key) =>
      cache.evict(userId, key);
  Future<Result<int>> clear(String userId) => cache.clear(userId);
  Future<Result<int>> size(String userId) => cache.size(userId);
}
