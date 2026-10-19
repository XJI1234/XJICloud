import '../../../shared/domain/result.dart';
import '../../../shared/domain/cancellation.dart';

/// A training artifact, deliberately not a ModelAsset or a public viewer URL.
final class TrainingOutputRef {
  const TrainingOutputRef({
    required this.id,
    required this.userId,
    required this.jobId,
    required this.sizeBytes,
  });
  final String id, userId, jobId;
  final int sizeBytes;
  String get fileName => 'training-$jobId.ply';
}

abstract interface class TrainingOutputPort {
  Future<Result<TrainingOutputRef>> download(
    String userId,
    String jobId,
    String url,
    Cancellation cancellation,
    void Function(int, int?) onProgress,
  );
  Future<Result<void>> remove(TrainingOutputRef output);
}
