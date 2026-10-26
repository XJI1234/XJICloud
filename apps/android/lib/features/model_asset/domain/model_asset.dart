final class ModelAsset {
  const ModelAsset({
    required this.id,
    required this.projectId,
    required this.fileName,
    required this.format,
    required this.sizeBytes,
    required this.version,
    required this.createdAt,
    required this.updatedAt,
  });
  final String id, projectId, fileName, format;
  final int sizeBytes, version;
  final DateTime createdAt, updatedAt;
}
