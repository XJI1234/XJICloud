import '../../../shared/domain/result.dart';
import 'local_file_port.dart';
import 'model_asset.dart';

/// Completion uncertainty is durable: restart must never implicitly POST again.
enum UploadPhase { ready, paused, completing, uncertain, completed }

final class UploadCheckpoint {
  const UploadCheckpoint({
    required this.userId,
    required this.projectId,
    required this.source,
    required this.sessionId,
    this.receivedBytes = 0,
    this.phase = UploadPhase.ready,
    this.model,
  });
  final String userId, projectId, sessionId;
  final LocalModelRef source;
  final int receivedBytes;
  final UploadPhase phase;
  final ModelAsset? model;
  UploadCheckpoint copy({
    int? receivedBytes,
    UploadPhase? phase,
    ModelAsset? model,
    LocalModelRef? source,
  }) => UploadCheckpoint(
    userId: userId,
    projectId: projectId,
    source: source ?? this.source,
    sessionId: sessionId,
    receivedBytes: receivedBytes ?? this.receivedBytes,
    phase: phase ?? this.phase,
    model: model ?? this.model,
  );
}

abstract interface class UploadCheckpointPort {
  Future<Result<UploadCheckpoint?>> read(String userId);
  Future<Result<void>> write(UploadCheckpoint checkpoint);
  Future<Result<void>> remove(String userId);
}
