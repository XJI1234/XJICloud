import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/features/model_asset/application/upload_model.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset_repository.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/upload_checkpoint_port.dart';

const source = LocalModelRef(
  id: 's',
  fileName: 'a.ply',
  sizeBytes: 10,
  fingerprint: 'sha',
);
final asset = ModelAsset(
  id: 'm',
  projectId: 'p',
  fileName: 'a.ply',
  format: 'PLY',
  sizeBytes: 10,
  version: 1,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026),
);

class Files implements LocalFilePort {
  bool changed = false;
  final ranges = <String>[];
  @override
  Future<Result<LocalModelRef?>> choose(Cancellation cancellation) async =>
      const Success(source);
  @override
  Future<Result<void>> verify(LocalModelRef source) async => changed
      ? const Failure(DomainError('source_changed'))
      : const Success(null);
  @override
  Stream<List<int>> read(LocalModelRef source, int start, int end) {
    ranges.add('$start-$end');
    return Stream.value(List.filled(end - start, 1));
  }

  @override
  Future<Result<void>> release(LocalModelRef source) async =>
      const Success(null);
}

class Journal implements UploadCheckpointPort {
  UploadCheckpoint? state;
  @override
  Future<Result<UploadCheckpoint?>> read(String userId) async => Success(state);
  @override
  Future<Result<void>> write(UploadCheckpoint checkpoint) async {
    state = checkpoint;
    return const Success(null);
  }

  @override
  Future<Result<void>> remove(String userId) async {
    state = null;
    return const Success(null);
  }
}

class Repo implements ModelAssetRepository {
  int received = 3, completes = 0, gets = 0;
  bool lost = false;
  String failureCode = 'network';
  Cancellation? cancelOnChunk;
  @override
  Future<Result<UploadSession>> getUploadSession(
    String id,
    int generation,
  ) async {
    gets++;
    return Success(
      UploadSession(
        id: id,
        chunkSizeBytes: 4,
        receivedBytes: received,
        sizeBytes: 10,
      ),
    );
  }

  @override
  Future<Result<UploadSession>> createUploadSession(
    String projectId,
    String fileName,
    int size,
    int generation,
  ) => getUploadSession('session', generation);
  @override
  Future<Result<int>> putChunk(
    String id,
    int start,
    int end,
    int total,
    Stream<List<int>> bytes,
    int generation,
    Cancellation cancellation,
  ) async {
    expect((await bytes.expand((x) => x).toList()).length, end - start);
    received = end;
    cancelOnChunk?.cancel();
    return Success(end);
  }

  @override
  Future<Result<ModelAsset>> completeUpload(String id, int generation) async {
    completes++;
    return lost ? Failure(DomainError(failureCode)) : Success(asset);
  }

  @override
  Future<Result<void>> abortUpload(String id, int generation) async =>
      const Success(null);
  @override
  Future<Result<void>> delete(String id, int generation) async =>
      const Success(null);
  @override
  Future<Result<List<ModelAsset>>> list(
    String projectId,
    int generation,
  ) async => Success([asset]);
  @override
  Future<Result<void>> downloadToFile(
    String id,
    int generation,
    Future<void> Function(Stream<List<int>>) consume,
    Cancellation cancellation,
  ) async => const Success(null);
}

UploadCheckpoint checkpoint({
  UploadPhase phase = UploadPhase.paused,
  int received = 9,
}) => UploadCheckpoint(
  userId: 'u',
  projectId: 'p',
  source: source,
  sessionId: 'session',
  receivedBytes: received,
  phase: phase,
);
void main() {
  test('GET server offset overrides local and tail chunk uses exclusive source end', () async {
    final repo = Repo(), files = Files(), journal = Journal();
    expect(
      await UploadModel(
        repo,
        files,
        journal,
      ).run(checkpoint(), 1, Cancellation(), () => true, onCheckpoint: (_) {}),
      isA<Success<ModelAsset>>(),
    );
    expect(files.ranges, ['3-7', '7-10']);
    expect(repo.gets, 1);
    expect(repo.completes, 1);
    expect(journal.state!.model!.id, 'm');
  });
  test('changed source prevents any GET or PUT', () async {
    final repo = Repo(), files = Files()..changed = true;
    final result = await UploadModel(
      repo,
      files,
      Journal(),
    ).run(checkpoint(), 1, Cancellation(), () => true, onCheckpoint: (_) {});
    expect((result as Failure).error.code, 'source_changed');
    expect(repo.gets, 0);
  });
  test('pause re-GETs server after ambiguous chunk acknowledgement', () async {
    final repo = Repo(),
        files = Files(),
        journal = Journal(),
        cancel = Cancellation();
    repo.cancelOnChunk = cancel;
    final upload = UploadModel(repo, files, journal);
    expect(
      await upload.run(
        checkpoint(),
        1,
        cancel,
        () => true,
        onCheckpoint: (_) {},
      ),
      isA<Failure<ModelAsset>>(),
    );
    expect(repo.completes, 0);
    repo.cancelOnChunk = null;
    await upload.run(
      journal.state!,
      1,
      Cancellation(),
      () => true,
      onCheckpoint: (_) {},
    );
    expect(files.ranges, ['3-7', '7-10']);
    expect(repo.gets, 2);
  });
  test('lost complete response remains uncertain across restart and no automatic POST', () async {
    final repo = Repo()..lost = true, journal = Journal();
    final upload = UploadModel(repo, Files(), journal);
    final result = await upload.run(
      checkpoint(),
      1,
      Cancellation(),
      () => true,
      onCheckpoint: (_) {},
    );
    expect((result as Failure).error.code, 'completion_uncertain');
    expect(journal.state!.phase, UploadPhase.uncertain);
    await UploadModel(
      repo,
      Files(),
      journal,
    ).run(journal.state!, 1, Cancellation(), () => true, onCheckpoint: (_) {});
    expect(repo.completes, 1);
    repo.failureCode = 'not_found';
    final retry = await upload.run(
      journal.state!,
      1,
      Cancellation(),
      () => true,
      onCheckpoint: (_) {},
      retryCompletion: true,
    );
    expect((retry as Failure).error.code, 'completion_uncertain');
    expect(journal.state!.phase, UploadPhase.uncertain);
    expect(repo.completes, 2);
  });
  test(
    'known completion identity bypasses repeated broken server complete',
    () async {
      final repo = Repo(), journal = Journal();
      final upload = UploadModel(repo, Files(), journal);
      await upload.run(
        checkpoint(),
        1,
        Cancellation(),
        () => true,
        onCheckpoint: (_) {},
      );
      repo.lost = true;
      final result = await upload.run(
        journal.state!,
        1,
        Cancellation(),
        () => true,
        onCheckpoint: (_) {},
      );
      expect((result as Success<ModelAsset>).value.id, 'm');
      expect(repo.completes, 1);
    },
  );
}
