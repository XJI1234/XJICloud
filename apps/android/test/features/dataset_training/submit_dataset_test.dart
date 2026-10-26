import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/application/prepare_dataset.dart';
import 'package:xjicloud_mobile/features/dataset_training/application/submit_dataset.dart';
import 'package:xjicloud_mobile/features/dataset_training/application/resume_dataset.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/dataset_manifest.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job_repository.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/selected_image.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/upload_checkpoint_port.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/object_storage_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';

import 'dataset_archive_test.dart' show image;

TrainingJob job([JobStatus status = JobStatus.uploading]) => TrainingJob(
  id: 'j',
  projectId: 'p',
  name: 'scan',
  status: status,
  progress: 0,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026),
);

class Jobs implements TrainingJobRepository {
  int creates = 0, completes = 0, gets = 0, cancels = 0;
  JobStatus status = JobStatus.uploading;
  bool createLost = false, completeLost = false;
  @override
  Future<Result<DatasetTicket>> create(
    String p,
    String n,
    DatasetManifest m,
    int g,
  ) async {
    creates++;
    if (createLost) return const Failure(DomainError('network'));
    return Success(
      DatasetTicket('j', {
        for (final f in m.files)
          f.archivedName: SignedUpload(
            f.archivedName,
            'https://oss.test/${f.archivedName}',
            f.contentType,
          ),
        'manifest.json': const SignedUpload(
          'manifest.json',
          'https://oss.test/manifest',
          'application/json',
        ),
      }, DateTime.now().add(const Duration(hours: 1))),
    );
  }

  @override
  Future<Result<TrainingJob>> complete(String p, String j, int g) async {
    completes++;
    status = JobStatus.queued;
    return completeLost
        ? const Failure(DomainError('network'))
        : Success(job(status));
  }

  @override
  Future<Result<TrainingJob>> get(String id, int g) async {
    gets++;
    return Success(job(status));
  }

  @override
  Future<Result<List<TrainingJob>>> list(String p, int g) async =>
      Success([job(status)]);
  @override
  Future<Result<TrainingJob>> cancel(String j, int g) async {
    cancels++;
    status = JobStatus.cancelled;
    return Success(job(status));
  }

  @override
  Future<Result<void>> delete(String j, int g) async => const Success(null);
}

class Sources implements ImageSourcePort {
  final released = <String>[];
  @override
  Future<Result<List<SelectedImage>?>> choose(bool d, Cancellation c) async =>
      Success([image(1), image(2)]);
  @override
  Future<Result<void>> verify(SelectedImage s) async => const Success(null);
  @override
  Stream<List<int>> read(SelectedImage s) =>
      Stream.value(List.filled(s.sizeBytes, 7));
  @override
  Future<Result<void>> release(SelectedImage s) async {
    released.add(s.sourceId);
    return const Success(null);
  }
}

class Journal implements UploadCheckpointPort {
  DatasetCheckpoint? state;
  final history = <DatasetCheckpoint>[];
  @override
  Future<Result<DatasetCheckpoint?>> read(String u) async => Success(state);
  @override
  Future<Result<void>> write(DatasetCheckpoint c) async {
    state = c;
    history.add(c);
    return const Success(null);
  }

  @override
  Future<Result<void>> remove(String u) async {
    state = null;
    return const Success(null);
  }
}

class Storage implements ObjectStoragePort {
  final calls = <String>[];
  String? failAt;
  String failure = 'signature_expired';
  Cancellation? pause;
  @override
  Future<Result<void>> put(
    String url,
    String type,
    int length,
    Stream<List<int>> bytes,
    Cancellation c,
    void Function(int) progress,
  ) async {
    calls.add(url);
    var n = 0;
    await for (final b in bytes) {
      n += b.length;
      progress(n);
    }
    if (url == failAt) return Failure(DomainError(failure));
    if (pause != null && calls.length == 2) {
      pause!.cancel();
      return const Failure(DomainError('cancelled'));
    }
    return const Success(null);
  }
}

DatasetManifest manifest() =>
    (prepareDataset([image(1), image(2)]) as Success<DatasetManifest>).value;
void main() {
  test(
    'replayable PUT retries only 1/2/4 seconds and stops after four attempts',
    () async {
      final repo = Jobs(),
          journal = Journal(),
          oss = Storage()
            ..failAt = 'https://oss.test/0001.jpg'
            ..failure = 'network';
      final delays = <int>[];
      final use = SubmitDataset(
        repo,
        Sources(),
        oss,
        journal,
        delay: (duration, c) async {
          delays.add(duration.inSeconds);
        },
      );
      final ticket = (await use.create(
        'u',
        'p',
        'scan',
        manifest(),
        1,
        () => true,
      ) as Success<DatasetTicket>).value;
      expect(
        await use.run(journal.state!, ticket, 1, Cancellation(), () => true),
        isA<Failure<TrainingJob>>(),
      );
      expect(delays, [1, 2, 4]);
      expect(oss.calls.length, 4);
      expect(repo.completes, 0);
    },
  );
  test(
    'sequential bytes and once manifest; all acknowledged before complete',
    () async {
      final repo = Jobs(),
          files = Sources(),
          journal = Journal(),
          oss = Storage();
      final use = SubmitDataset(repo, files, oss, journal);
      final ticket = (await use.create(
        'u',
        'p',
        'scan',
        manifest(),
        1,
        () => true,
      ) as Success<DatasetTicket>).value;
      final progress = <int>[];
      final result = await use.run(
        journal.state!,
        ticket,
        1,
        Cancellation(),
        () => true,
        onProgress: (n, total) => progress.add(n),
      );
      expect(result, isA<Success<TrainingJob>>());
      expect(repo.completes, 1);
      expect(oss.calls.length, 3);
      expect(oss.calls.where((x) => x.endsWith('manifest')).length, 1);
      expect(progress.last, manifest().totalBytes);
      expect(
        journal.history
            .where((s) => s.phase == DatasetUploadPhase.completing)
            .single
            .confirmed
            .length,
        3,
      );
    },
  );
  test(
    'Nth image failure never completes and signature failure is not retried',
    () async {
      final repo = Jobs(),
          journal = Journal(),
          oss = Storage()..failAt = 'https://oss.test/0002.jpg';
      final use = SubmitDataset(repo, Sources(), oss, journal);
      final t = (await use.create(
        'u',
        'p',
        'scan',
        manifest(),
        1,
        () => true,
      ) as Success<DatasetTicket>).value;
      expect(
        await use.run(journal.state!, t, 1, Cancellation(), () => true),
        isA<Failure<TrainingJob>>(),
      );
      expect(repo.completes, 0);
      expect(oss.calls.length, 2);
      expect(journal.state!.confirmed, {'0001.jpg'});
    },
  );
  test('unknown current PUT is replayed on same job after GET', () async {
    final repo = Jobs(),
        journal = Journal(),
        cancel = Cancellation(),
        oss = Storage()..pause = Cancellation();
    oss.pause = cancel;
    final use = SubmitDataset(repo, Sources(), oss, journal);
    final t = (await use.create(
      'u',
      'p',
      'scan',
      manifest(),
      1,
      () => true,
    ) as Success<DatasetTicket>).value;
    await use.run(journal.state!, t, 1, cancel, () => true);
    expect(journal.state!.confirmed, {'0001.jpg'});
    oss.pause = null;
    final result = await ResumeDataset(use)(
      journal.state!,
      t,
      1,
      Cancellation(),
      () => true,
    );
    expect(result, isA<Success<TrainingJob>>());
    expect(oss.calls.where((x) => x.endsWith('0001.jpg')).length, 1);
    expect(oss.calls.where((x) => x.endsWith('0002.jpg')).length, 2);
    expect(repo.gets, 1);
  });
  test(
    'lost complete response queries QUEUED without repeating POST',
    () async {
      final repo = Jobs()..completeLost = true, journal = Journal();
      final use = SubmitDataset(repo, Sources(), Storage(), journal);
      final t = (await use.create(
        'u',
        'p',
        'scan',
        manifest(),
        1,
        () => true,
      ) as Success<DatasetTicket>).value;
      expect(
        await use.run(journal.state!, t, 1, Cancellation(), () => true),
        isA<Success<TrainingJob>>(),
      );
      expect(repo.completes, 1);
      expect(repo.gets, 1);
    },
  );
  test(
    'lost create response preserves uncertain intent and never auto creates',
    () async {
      final repo = Jobs()..createLost = true, journal = Journal();
      final use = SubmitDataset(repo, Sources(), Storage(), journal);
      final result = await use.create(
        'u',
        'p',
        'scan',
        manifest(),
        1,
        () => true,
      );
      expect(
        (result as Failure<DatasetTicket>).error.code,
        'dataset_create_uncertain',
      );
      expect(repo.creates, 1);
      expect(journal.state!.jobId, isNull);
      expect(journal.state!.phase, DatasetUploadPhase.uncertain);
    },
  );
  test(
    'cold restart checks job then requires explicit cancel and new submission',
    () async {
      final repo = Jobs(), journal = Journal();
      final use = SubmitDataset(repo, Sources(), Storage(), journal);
      await use.create('u', 'p', 'scan', manifest(), 1, () => true);
      final r = await ResumeDataset(use)(
        journal.state!,
        null,
        1,
        Cancellation(),
        () => true,
      );
      expect(
        (r as Failure<TrainingJob>).error.code,
        'dataset_restart_required',
      );
      expect(repo.gets, 1);
      expect(repo.creates, 1);
      expect(repo.cancels, 0);
    },
  );
}
