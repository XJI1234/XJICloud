import 'dart:async';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/dataset_manifest.dart';
import '../domain/selected_image.dart';
import '../domain/object_storage_port.dart';
import '../domain/training_job_repository.dart';
import '../domain/training_job.dart';
import '../domain/upload_checkpoint_port.dart';

/// Each acknowledged object is durable before the next send. Only PUT is replayable.
final class SubmitDataset {
  SubmitDataset(
    this.repository,
    this.sources,
    this.storage,
    this.checkpoints, {
    Future<void> Function(Duration, Cancellation)? delay,
  }) : delay = delay ?? _delay;
  final TrainingJobRepository repository;
  final ImageSourcePort sources;
  final ObjectStoragePort storage;
  final UploadCheckpointPort checkpoints;
  final Future<void> Function(Duration, Cancellation) delay;
  static Future<void> _delay(
    Duration duration,
    Cancellation cancellation,
  ) async {
    final done = Completer<void>();
    final timer = Timer(duration, () => done.complete());
    final detach = cancellation.listen(() {
      if (!done.isCompleted) done.complete();
    });
    await done.future;
    timer.cancel();
    detach();
  }

  Future<Result<DatasetTicket>> create(
    String user,
    String project,
    String name,
    DatasetManifest manifest,
    int generation,
    bool Function() current, {
    void Function(DatasetCheckpoint)? onCheckpoint,
    Cancellation? cancellation,
  }) async {
    if (!current()) return const Failure(DomainError('cancelled'));
    var state = DatasetCheckpoint(
      userId: user,
      projectId: project,
      name: name.trim(),
      manifest: manifest,
    );
    final saved = await checkpoints.write(state);
    if (saved case Failure<void>(:final error)) return Failure(error);
    if (!current()) return const Failure(DomainError('cancelled'));
    onCheckpoint?.call(state);
    if (cancellation?.isCancelled == true) {
      return const Failure(DomainError('cancelled'));
    }
    final result = await repository.create(
      project,
      state.name,
      manifest,
      generation,
    );
    if (!current()) return const Failure(DomainError('cancelled'));
    state = result is Success<DatasetTicket>
        ? state.copy(
            jobId: result.value.jobId,
            phase: DatasetUploadPhase.uploading,
          )
        : state.copy(phase: DatasetUploadPhase.uncertain);
    // Publish known server identity even if local persistence fails: never lose it in-process.
    onCheckpoint?.call(state);
    final persisted = await checkpoints.write(state);
    if (persisted case Failure<void>(:final error)) return Failure(error);
    return result is Success<DatasetTicket>
        ? result
        : const Failure(DomainError('dataset_create_uncertain'));
  }

  Future<Result<TrainingJob>> run(
    DatasetCheckpoint initial,
    DatasetTicket ticket,
    int generation,
    Cancellation cancellation,
    bool Function() current, {
    bool retryCompletion = false,
    void Function(DatasetCheckpoint)? onCheckpoint,
    void Function(int, int)? onProgress,
  }) async {
    var state = initial;
    bool active() => current() && !cancellation.isCancelled;
    if (!active()) return const Failure(DomainError('cancelled'));
    if (ticket.jobId != state.jobId ||
        DateTime.now().isAfter(ticket.expiresAt)) {
      return const Failure(DomainError('dataset_restart_required'));
    }
    if (state.phase == DatasetUploadPhase.completing && !retryCompletion) {
      return const Failure(DomainError('completion_uncertain'));
    }
    var acknowledgedBytes = state.manifest.files
        .where((f) => state.confirmed.contains(f.archivedName))
        .fold(
          state.confirmed.contains('manifest.json')
              ? state.manifest.bytes.length
              : 0,
          (n, f) => n + f.source.sizeBytes,
        );
    Future<Result<void>> save(DatasetCheckpoint next) async {
      if (!active()) return const Failure(DomainError('cancelled'));
      final saved = await checkpoints.write(next);
      if (saved is Success<void>) {
        state = next;
        if (current()) onCheckpoint?.call(next);
      }
      return saved;
    }

    Future<Result<void>> put(
      String name,
      String type,
      int size,
      Stream<List<int>> Function() open,
    ) async {
      if (state.confirmed.contains(name)) return const Success(null);
      final signed = ticket.uploads[name];
      if (signed == null || signed.contentType != type) {
        return const Failure(DomainError('protocol'));
      }
      for (var attempt = 0; attempt < 4; attempt++) {
        if (!active()) return const Failure(DomainError('cancelled'));
        final result = await storage.put(
          signed.url,
          type,
          size,
          open(),
          cancellation,
          (sent) {
            if (active()) {
              onProgress?.call(
                acknowledgedBytes + sent,
                state.manifest.totalBytes,
              );
            }
          },
        );
        if (!active()) return const Failure(DomainError('cancelled'));
        if (result is Success<void>) {
          final persisted = await save(
            state.copy(confirmed: {...state.confirmed, name}),
          );
          if (persisted is Success<void>) {
            acknowledgedBytes += size;
          }
          return persisted;
        }
        final error = (result as Failure<void>).error;
        if (attempt == 3 || !['network', 'server'].contains(error.code)) {
          return result;
        }
        await delay(Duration(seconds: 1 << attempt), cancellation);
      }
      return const Failure(DomainError('network'));
    }

    onProgress?.call(acknowledgedBytes, state.manifest.totalBytes);
    for (final image in state.manifest.files) {
      if (!active()) return const Failure(DomainError('cancelled'));
      if (state.confirmed.contains(image.archivedName)) continue;
      final verified = await sources.verify(image.source);
      if (verified case Failure<void>(:final error)) return Failure(error);
      final uploaded = await put(
        image.archivedName,
        image.contentType,
        image.source.sizeBytes,
        () => sources.read(image.source),
      );
      if (uploaded case Failure<void>(:final error)) return Failure(error);
    }
    final bytes = state.manifest.bytes;
    final manifest = await put(
      'manifest.json',
      'application/json',
      bytes.length,
      () => Stream.value(bytes),
    );
    if (manifest case Failure<void>(:final error)) return Failure(error);
    final saved = await save(state.copy(phase: DatasetUploadPhase.completing));
    if (saved case Failure<void>(:final error)) return Failure(error);
    if (!active()) return const Failure(DomainError('cancelled'));
    var completed = await repository.complete(
      state.projectId,
      state.jobId!,
      generation,
    );
    if (!active()) return const Failure(DomainError('cancelled'));
    if (completed is Failure<TrainingJob>) {
      // A rejected/unknown response can follow a committed complete. Never retry blindly.
      final actual = await repository.get(state.jobId!, generation);
      if (!active()) return const Failure(DomainError('cancelled'));
      if (actual is Success<TrainingJob> &&
          actual.value.status != JobStatus.uploading) {
        completed = actual;
      } else {
        return const Failure(DomainError('completion_uncertain'));
      }
    }
    final persisted = await save(
      state.copy(phase: DatasetUploadPhase.completed),
    );
    if (persisted case Failure<void>(:final error)) return Failure(error);
    return completed;
  }
}
