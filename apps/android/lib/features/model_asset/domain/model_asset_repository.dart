import '../../../shared/domain/result.dart';
import '../../../shared/domain/cancellation.dart';
import 'model_asset.dart';

final class UploadSession {
  const UploadSession({
    required this.id,
    required this.chunkSizeBytes,
    required this.receivedBytes,
    required this.sizeBytes,
  });
  final String id;
  final int chunkSizeBytes, receivedBytes, sizeBytes;
}

typedef TransferProgress = void Function(int received, int total);

abstract interface class ModelAssetRepository {
  Future<Result<List<ModelAsset>>> list(String projectId, int generation);
  Future<Result<UploadSession>> createUploadSession(
    String projectId,
    String fileName,
    int sizeBytes,
    int generation,
  );
  Future<Result<UploadSession>> getUploadSession(String id, int generation);
  Future<Result<int>> putChunk(
    String id,
    int start,
    int endExclusive,
    int total,
    Stream<List<int>> bytes,
    int generation,
    Cancellation cancellation,
  );
  Future<Result<ModelAsset>> completeUpload(String id, int generation);
  Future<Result<void>> abortUpload(String id, int generation);
  Future<Result<void>> downloadToFile(
    String id,
    int generation,
    Future<void> Function(Stream<List<int>>) consume,
    Cancellation cancellation,
  );
  Future<Result<void>> delete(String id, int generation);
}
