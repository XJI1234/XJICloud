import 'model_asset.dart';

final class ModelRevision {
  const ModelRevision({
    required this.userId,
    required this.modelId,
    required this.updatedAt,
    required this.sizeBytes,
  });
  factory ModelRevision.of(String userId, ModelAsset asset) => ModelRevision(
    userId: userId,
    modelId: asset.id,
    updatedAt: asset.updatedAt,
    sizeBytes: asset.sizeBytes,
  );
  final String userId, modelId;
  final DateTime updatedAt;
  final int sizeBytes;
  String get key =>
      '${Uri.encodeComponent(userId)}_${Uri.encodeComponent(modelId)}_${updatedAt.microsecondsSinceEpoch}_$sizeBytes';
}
