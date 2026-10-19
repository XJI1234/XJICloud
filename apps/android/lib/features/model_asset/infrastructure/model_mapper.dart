import '../domain/model_asset.dart';
import '../domain/model_asset_repository.dart';
import '../domain/model_policy.dart';

abstract final class ModelMapper {
  static final _id = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  static bool validId(String id) => _id.hasMatch(id);
  static ModelAsset fromJson(Object? data) {
    if (data is! Map<String, dynamic> ||
        data['id'] is! String ||
        !validId(data['id']) ||
        data['projectId'] is! String ||
        !validId(data['projectId']) ||
        data['fileName'] is! String ||
        data['sizeBytes'] is! int ||
        data['sizeBytes'] < 1 ||
        data['version'] is! int ||
        data['version'] < 1 ||
        !['PLY', 'SPZ'].contains(data['format']) ||
        data['createdAt'] is! String ||
        data['updatedAt'] is! String) {
      throw const FormatException('model');
    }
    final created = DateTime.tryParse(data['createdAt']);
    final updated = DateTime.tryParse(data['updatedAt']);
    if (created == null || updated == null) throw const FormatException('time');
    return ModelAsset(
      id: data['id'],
      projectId: data['projectId'],
      fileName: data['fileName'],
      format: data['format'],
      sizeBytes: data['sizeBytes'],
      version: data['version'],
      createdAt: created,
      updatedAt: updated,
    );
  }

  static Map<String, dynamic> toJson(ModelAsset model) => {
    'id': model.id,
    'projectId': model.projectId,
    'fileName': model.fileName,
    'format': model.format,
    'sizeBytes': model.sizeBytes,
    'version': model.version,
    'createdAt': model.createdAt.toUtc().toIso8601String(),
    'updatedAt': model.updatedAt.toUtc().toIso8601String(),
  };
  static UploadSession session(Object? data) {
    if (data is! Map<String, dynamic> ||
        data['sessionId'] is! String ||
        !validId(data['sessionId']) ||
        data['chunkSizeBytes'] is! int ||
        data['chunkSizeBytes'] < 1 ||
        data['chunkSizeBytes'] > ModelPolicy.maxSizeBytes ||
        data['sizeBytes'] is! int ||
        data['sizeBytes'] < 1 ||
        data['sizeBytes'] > ModelPolicy.maxSizeBytes ||
        data['receivedBytes'] is! int ||
        data['receivedBytes'] < 0 ||
        data['receivedBytes'] > data['sizeBytes']) {
      throw const FormatException('session');
    }
    return UploadSession(
      id: data['sessionId'],
      chunkSizeBytes: data['chunkSizeBytes'],
      receivedBytes: data['receivedBytes'],
      sizeBytes: data['sizeBytes'],
    );
  }
}
