import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/upload_controller.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/selected_image.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/dataset_manifest.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job_repository.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_output_port.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'submit_dataset_test.dart' show Jobs, Journal, Storage, Sources, job;

import 'package:xjicloud_mobile/features/dataset_training/domain/training_job.dart';

import 'job_policy_test.dart' show Events;
import 'dataset_archive_test.dart' show image;

class Picker extends Sources {
  final pending = Completer<Result<List<SelectedImage>?>>();
  Cancellation? token;
  @override
  Future<Result<List<SelectedImage>?>> choose(bool dir, Cancellation c) {
    token = c;
    return pending.future;
  }
}

class CreatingJobs extends Jobs {
  final entered = Completer<void>(), release = Completer<void>();
  @override
  Future<Result<DatasetTicket>> create(
    String p,
    String n,
    DatasetManifest m,
    int g,
  ) async {
    final result = await super.create(p, n, m, g);
    entered.complete();
    await release.future;
    return result;
  }
}

class FailedRemoval extends Journal {
  bool fail = true;
  @override
  Future<Result<void>> remove(String user) async =>
      fail ? const Failure(DomainError('storage')) : super.remove(user);
}

class Outputs implements TrainingOutputPort {
  final removed = <String>[];
  @override
  Future<Result<TrainingOutputRef>> download(
    String u,
    String j,
    String url,
    Cancellation c,
    void Function(int, int?) progress,
  ) async => Success(
    TrainingOutputRef(id: 'output', userId: u, jobId: j, sizeBytes: 3),
  );
  @override
  Future<Result<void>> remove(TrainingOutputRef output) async {
    removed.add(output.id);
    return const Success(null);
  }
}

class EmptyPicker extends Sources {
  Result<List<SelectedImage>?> selection = const Success(null);
  @override
  Future<Result<List<SelectedImage>?>> choose(bool dir, Cancellation c) async =>
      selection;
}

void main() {
  test('cancelled empty or failed preparation releases target before project B selection', () async {
    final s = SessionCoordinator(MemoryStore());
    await s.activate(session());
    addTearDown(s.dispose);
    for (final result in <Result<List<SelectedImage>?>>[
      const Success(null),
      const Success([]),
      const Failure(DomainError('storage')),
    ]) {
      final sources = EmptyPicker()..selection = result,
          c = UploadController(
            Jobs(),
            sources,
            Storage(),
            Journal(),
            Events(),
            s,
          );
      c.synchronize();
      await Future<void>.delayed(Duration.zero);
      await c.choose('project-A');
      expect(c.draft, isNull);
      expect(c.checkpoint, isNull);
      expect(c.targetProjectId, isNull);
      sources.selection = Success([image(1)]);
      await c.choose('project-B');
      expect(c.targetProjectId, 'project-B');
      expect(c.draft, isNotNull);
      c.dispose();
    }
  });
  test(
    'logout releases issued private outputs and clears their references',
    () async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      addTearDown(s.dispose);
      final output = Outputs();
      final c = UploadController(
        Jobs(),
        Sources(),
        Storage(),
        Journal(),
        Events(),
        s,
        outputs: output,
      );
      c.synchronize();
      addTearDown(c.dispose);
      await Future<void>.delayed(Duration.zero);
      final j = job(JobStatus.completed);
      final full = TrainingJob(
        id: j.id,
        projectId: j.projectId,
        name: j.name,
        status: j.status,
        progress: j.progress,
        createdAt: j.createdAt,
        updatedAt: j.updatedAt,
        downloadUrl: 'https://oss.test/output',
      );
      await c.downloadOutput(full);
      expect(c.outputFor(j.id), isNotNull);
      await s.invalidate();
      c.synchronize();
      await Future<void>.delayed(Duration.zero);
      expect(output.removed, ['output']);
      expect(c.outputFor(j.id), isNull);
    },
  );
  test(
    'logout releases unsubmitted snapshots without touching another account',
    () async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      addTearDown(s.dispose);
      final sources = Sources(),
          c = UploadController(
            Jobs(),
            sources,
            Storage(),
            Journal(),
            Events(),
            s,
          );
      c.synchronize();
      addTearDown(c.dispose);
      await Future<void>.delayed(Duration.zero);
      await c.choose('p');
      await s.invalidate();
      c.synchronize();
      await Future<void>.delayed(Duration.zero);
      expect(sources.released, ['1', '2']);
      expect(c.draft, isNull);
    },
  );
  test('expired signature retires all in-memory URLs and resume requires new submission', () async {
    final s = SessionCoordinator(MemoryStore());
    await s.activate(session());
    addTearDown(s.dispose);
    final repo = Jobs(),
        oss = Storage()..failAt = 'https://oss.test/0001.jpg',
        c = UploadController(repo, Sources(), oss, Journal(), Events(), s);
    c.synchronize();
    addTearDown(c.dispose);
    await Future<void>.delayed(Duration.zero);
    await c.choose('p');
    await c.submit('scan');
    expect(oss.calls.length, 1);
    await c.resume();
    expect(oss.calls.length, 1);
    expect(c.error!.code, 'dataset_restart_required');
    expect(repo.creates, 1);
  });
  test('finishing acknowledged tracking never cancels an already queued training job', () async {
    final s = SessionCoordinator(MemoryStore());
    await s.activate(session());
    addTearDown(s.dispose);
    final repo = Jobs(),
        journal = FailedRemoval(),
        c = UploadController(repo, Sources(), Storage(), journal, Events(), s);
    c.synchronize();
    addTearDown(c.dispose);
    await Future<void>.delayed(Duration.zero);
    await c.choose('p');
    await c.submit('scan');
    expect(c.checkpoint, isNotNull);
    journal.fail = false;
    expect(await c.discard(), isTrue);
    expect(repo.cancels, 0);
  });
  test('background during create preserves acknowledged job identity without sending PUTs', () async {
    final s = SessionCoordinator(MemoryStore());
    await s.activate(session());
    addTearDown(s.dispose);
    final repo = CreatingJobs(),
        oss = Storage(),
        c = UploadController(repo, Sources(), oss, Journal(), Events(), s);
    c.synchronize();
    addTearDown(c.dispose);
    await Future<void>.delayed(Duration.zero);
    await c.choose('p');
    final submission = c.submit('scan');
    await repo.entered.future;
    c.background();
    repo.release.complete();
    await submission;
    expect(c.checkpoint!.jobId, 'j');
    expect(c.paused, isTrue);
    expect(oss.calls, isEmpty);
    expect(repo.completes, 0);
  });
  test(
    'picker target remains fixed; background does not cancel system picker',
    () async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      addTearDown(s.dispose);
      final picker = Picker(),
          repo = Jobs(),
          c = UploadController(repo, picker, Storage(), Journal(), Events(), s);
      addTearDown(c.dispose);
      c.synchronize();
      await Future<void>.delayed(Duration.zero);
      final selected = c.choose('p');
      c.background();
      expect(picker.token!.isCancelled, isFalse);
      picker.pending.complete(Success([image(1)]));
      await selected;
      expect(c.targetProjectId, 'p');
      expect(c.draft!.files.length, 1);
      expect(repo.creates, 0);
      c.foreground();
    },
  );
  test('old-account picker callback releases sources and cannot populate new account', () async {
    final s = SessionCoordinator(MemoryStore());
    await s.activate(session());
    addTearDown(s.dispose);
    final picker = Picker(),
        c = UploadController(Jobs(), picker, Storage(), Journal(), Events(), s);
    addTearDown(c.dispose);
    c.synchronize();
    await Future<void>.delayed(Duration.zero);
    final selected = c.choose('p');
    await s.invalidate();
    c.synchronize();
    picker.pending.complete(Success([image(1)]));
    await selected;
    expect(c.draft, isNull);
    expect(picker.released, ['1']);
  });
  test(
    'project cleanup cancels and awaits picker and prevents late sends',
    () async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      addTearDown(s.dispose);
      final picker = Picker(),
          repo = Jobs(),
          c = UploadController(repo, picker, Storage(), Journal(), Events(), s);
      addTearDown(c.dispose);
      c.synchronize();
      await Future<void>.delayed(Duration.zero);
      final selected = c.choose('p');
      final stop = c.stopProjectTransfers('p');
      await Future<void>.delayed(Duration.zero);
      expect(picker.token!.isCancelled, isTrue);
      picker.pending.complete(Success([image(1)]));
      await selected;
      expect(await stop, isA<Success<void>>());
      expect(repo.creates, 0);
      expect(c.draft, isNull);
      expect(picker.released, ['1']);
    },
  );
}
