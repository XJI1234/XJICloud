import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/model_asset.dart';
import '../domain/model_asset_repository.dart';
import '../domain/model_policy.dart';
import '../domain/local_file_port.dart';
import '../domain/upload_checkpoint_port.dart';

final class UploadModel {
  UploadModel(this.repository, this.files, this.checkpoints);
  final ModelAssetRepository repository;
  final LocalFilePort files;
  final UploadCheckpointPort checkpoints;
  Future<Result<UploadCheckpoint>> create(
    String userId,
    String projectId,
    LocalModelRef source,
    int generation,
    bool Function() isCurrent, {
    Cancellation? cancellation,
    void Function(UploadCheckpoint)? onCheckpoint,
  }) async {
    final invalid = ModelPolicy.validate(source.fileName, source.sizeBytes);
    if (invalid != null) return Failure(invalid);
    final verified = await files.verify(source);
    if (verified case Failure<void>(:final error)) return Failure(error);
    if (!isCurrent() || cancellation?.isCancelled == true) {
      return const Failure(DomainError('cancelled'));
    }
    final result = await repository.createUploadSession(
      projectId,
      source.fileName,
      source.sizeBytes,
      generation,
    );
    if (!isCurrent()) return const Failure(DomainError('cancelled'));
    if (result case Failure<UploadSession>(:final error)) {
      return Failure(
        error.code == 'network'
            ? const DomainError('upload_create_uncertain')
            : error,
      );
    }
    final session = (result as Success<UploadSession>).value;
    if (session.sizeBytes != source.sizeBytes) {
      return const Failure(DomainError('protocol'));
    }
    final checkpoint = UploadCheckpoint(
      userId: userId,
      projectId: projectId,
      source: source,
      sessionId: session.id,
    );
    onCheckpoint?.call(checkpoint);
    final saved = await checkpoints.write(checkpoint);
    if (saved case Failure<void>(:final error)) return Failure(error);
    return Success(checkpoint);
  }

  Future<Result<ModelAsset>> run(
    UploadCheckpoint checkpoint,
    int generation,
    Cancellation cancellation,
    bool Function() isCurrent, {
    required void Function(UploadCheckpoint) onCheckpoint,
    bool retryCompletion = false,
  }) async {
    var state = checkpoint;
    bool active() => isCurrent() && !cancellation.isCancelled;
    Future<bool> save(UploadCheckpoint next) async {
      if (!isCurrent()) return false;
      final result = await checkpoints.write(next);
      if (result is Failure<void>) return false;
      state = next;
      if (isCurrent()) onCheckpoint(next);
      return true;
    }

    if (state.model != null) return Success(state.model!);
    if (!active()) return const Failure(DomainError('cancelled'));
    if ((state.phase == UploadPhase.uncertain ||
            state.phase == UploadPhase.completing) &&
        !retryCompletion) {
      return const Failure(DomainError('completion_uncertain'));
    }
    final verified = await files.verify(state.source);
    if (!active()) return const Failure(DomainError('cancelled'));
    if (verified case Failure<void>(:final error)) return Failure(error);
    final resumed = await repository.getUploadSession(
      state.sessionId,
      generation,
    );
    if (!active()) return const Failure(DomainError('cancelled'));
    if (resumed case Failure<UploadSession>(:final error)) {
      return Failure(error);
    }
    final session = (resumed as Success<UploadSession>).value;
    if (session.id != state.sessionId ||
        session.sizeBytes != state.source.sizeBytes ||
        session.chunkSizeBytes < 1 ||
        session.chunkSizeBytes > ModelPolicy.maxSizeBytes ||
        session.receivedBytes < 0 ||
        session.receivedBytes > session.sizeBytes) {
      return const Failure(DomainError('protocol'));
    }
    if (!await save(
      state.copy(
        receivedBytes: session.receivedBytes,
        phase: UploadPhase.ready,
      ),
    )) {
      return const Failure(DomainError('storage'));
    }
    while (state.receivedBytes < session.sizeBytes) {
      if (!active()) return const Failure(DomainError('cancelled'));
      final start = state.receivedBytes;
      final end = (start + session.chunkSizeBytes).clamp(0, session.sizeBytes);
      final sent = await repository.putChunk(
        state.sessionId,
        start,
        end,
        session.sizeBytes,
        files.read(state.source, start, end),
        generation,
        cancellation,
      );
      if (!active() || sent is Failure<int>) {
        if (isCurrent()) await save(state.copy(phase: UploadPhase.paused));
        return !active()
            ? const Failure(DomainError('cancelled'))
            : Failure((sent as Failure<int>).error);
      }
      final received = (sent as Success<int>).value;
      if (received <= start || received > session.sizeBytes) {
        return const Failure(DomainError('protocol'));
      }
      if (!await save(state.copy(receivedBytes: received))) {
        return const Failure(DomainError('storage'));
      }
    }
    if (!active()) return const Failure(DomainError('cancelled'));
    // Durable before POST. A process death here requires explicit reconciliation.
    if (!await save(state.copy(phase: UploadPhase.completing))) {
      return const Failure(DomainError('storage'));
    }
    if (!active()) return const Failure(DomainError('cancelled'));
    final completed = await repository.completeUpload(
      state.sessionId,
      generation,
    );
    if (!isCurrent()) return const Failure(DomainError('cancelled'));
    if (completed case Success<ModelAsset>(:final value)) {
      if (value.projectId != state.projectId ||
          value.sizeBytes != state.source.sizeBytes) {
        await save(state.copy(phase: UploadPhase.uncertain));
        return const Failure(DomainError('completion_uncertain'));
      }
      if (!await save(state.copy(phase: UploadPhase.completed, model: value))) {
        // Publish known remote identity even if durable metadata failed.
        onCheckpoint(state.copy(phase: UploadPhase.completed, model: value));
        return const Failure(DomainError('upload_completed_storage'));
      }
      return Success(value);
    }
    await save(state.copy(phase: UploadPhase.uncertain));
    return const Failure(DomainError('completion_uncertain'));
  }
}
