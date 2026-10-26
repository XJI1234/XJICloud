import '../../../shared/domain/result.dart';
import 'dataset_manifest.dart';

enum DatasetUploadPhase {
  creating,
  uncertain,
  uploading,
  completing,
  completed,
}

final class DatasetCheckpoint {
  DatasetCheckpoint({
    required this.userId,
    required this.projectId,
    required this.name,
    required this.manifest,
    this.jobId,
    this.phase = DatasetUploadPhase.creating,
    Set<String> confirmed = const {},
  }) : confirmed = Set.unmodifiable(confirmed);
  final String userId, projectId, name;
  final String? jobId;
  final DatasetManifest manifest;
  final DatasetUploadPhase phase;
  final Set<String> confirmed;
  DatasetCheckpoint copy({
    String? jobId,
    DatasetUploadPhase? phase,
    Set<String>? confirmed,
  }) => DatasetCheckpoint(
    userId: userId,
    projectId: projectId,
    name: name,
    manifest: manifest,
    jobId: jobId ?? this.jobId,
    phase: phase ?? this.phase,
    confirmed: confirmed ?? this.confirmed,
  );
}

abstract interface class UploadCheckpointPort {
  Future<Result<DatasetCheckpoint?>> read(String userId);
  Future<Result<void>> write(DatasetCheckpoint checkpoint);
  Future<Result<void>> remove(String userId);
}
