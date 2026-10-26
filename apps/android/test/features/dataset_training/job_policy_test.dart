import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/job_policy.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/job_event_port.dart';
import 'package:xjicloud_mobile/features/dataset_training/application/watch_job.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';

import 'submit_dataset_test.dart' show Jobs, job;

class Events implements JobEventPort {
  final streams = <StreamController<JobProgress>>[];
  @override
  Stream<JobProgress> subscribe(String id, int g, Cancellation c) {
    final stream = StreamController<JobProgress>();
    streams.add(stream);
    c.listen(() => stream.close());
    return stream.stream;
  }

  void emit(JobStatus s) => streams.last.add(
    JobProgress(jobId: 'j', status: s, progress: 75, message: '状态'),
  );
}

class DelayedJobs extends Jobs {
  final pending = <Completer<Result<TrainingJob>>>[];
  @override
  Future<Result<TrainingJob>> get(String id, int g) {
    gets++;
    final c = Completer<Result<TrainingJob>>();
    pending.add(c);
    return c.future;
  }
}

void main() {
  testWidgets('continuous healthy events cannot postpone the 15 second GET', (
    tester,
  ) async {
    final repo = Jobs(),
        events = Events(),
        registry = JobWatchers(repo, events);
    final lease = registry.retain('j', 1, () => true, (_) {});
    await tester.pump();
    for (var i = 0; i < 3; i++) {
      await tester.pump(const Duration(seconds: 5));
      events.emit(JobStatus.running);
      await tester.pump();
    }
    expect(repo.gets, 2);
    lease.release();
    registry.dispose();
  });
  test('actual cancel and terminal delete policy', () {
    for (final s in JobStatus.values) {
      expect(
        canCancelJob(s),
        s != JobStatus.completed && s != JobStatus.cancelled,
      );
      expect(
        canDeleteJob(s),
        [
          JobStatus.completed,
          JobStatus.failed,
          JobStatus.cancelled,
        ].contains(s),
      );
    }
  });
  testWidgets(
    'shared watcher GETs immediately and every 15 seconds on healthy SSE',
    (tester) async {
      final repo = Jobs(),
          events = Events(),
          registry = JobWatchers(repo, events);
      final values = <TrainingJob>[];
      final first = registry.retain('j', 1, () => true, (j) => values.add(j));
      final second = registry.retain('j', 1, () => true, (_) {});
      await tester.pump();
      expect(repo.gets, 1);
      expect(events.streams.length, 1);
      repo.status = JobStatus.completed;
      await tester.pump(const Duration(seconds: 15));
      await tester.pump();
      expect(repo.gets, 2);
      expect(values.last.status, JobStatus.completed);
      first.release();
      second.release();
      registry.dispose();
    },
  );
  testWidgets(
    'late GET cannot overwrite new SSE; terminal event fetches full output',
    (tester) async {
      final repo = DelayedJobs(),
          events = Events(),
          registry = JobWatchers(repo, events);
      final values = <TrainingJob>[];
      final lease = registry.retain('j', 1, () => true, values.add);
      await tester.pump();
      events.emit(JobStatus.running);
      await tester.pump();
      repo.pending.first.complete(Success(job()));
      await tester.pump();
      expect(values.where((j) => j.status == JobStatus.uploading), isEmpty);
      events.emit(JobStatus.completed);
      await tester.pump();
      expect(repo.gets, 2);
      final full = TrainingJob(
        id: 'j',
        projectId: 'p',
        name: 'scan',
        status: JobStatus.completed,
        progress: 100,
        downloadUrl: 'https://oss.test/output',
        createdAt: DateTime.utc(2026),
        updatedAt: DateTime.utc(2026),
      );
      repo.pending.last.complete(Success(full));
      await tester.pump();
      expect(values.last.downloadUrl, full.downloadUrl);
      await tester.pump(const Duration(seconds: 30));
      expect(repo.gets, 2);
      lease.release();
      registry.dispose();
    },
  );
  testWidgets(
    'CANCELLED closes SSE but reconciles worker RUNNING and COMPLETED',
    (tester) async {
      final repo = Jobs(),
          events = Events(),
          registry = JobWatchers(repo, events);
      final values = <TrainingJob>[];
      final lease = registry.retain('j', 1, () => true, values.add);
      await tester.pump();
      events.emit(JobStatus.cancelled);
      await tester.pump();
      repo.status = JobStatus.running;
      await tester.pump(const Duration(seconds: 15));
      await tester.pump();
      expect(values.last.status, JobStatus.running);
      expect(events.streams.length, 2);
      repo.status = JobStatus.completed;
      await tester.pump(const Duration(seconds: 15));
      await tester.pump();
      expect(values.last.status, JobStatus.completed);
      lease.release();
      registry.dispose();
    },
  );
  testWidgets(
    'failed SSE polls in five seconds; background and last release stop work',
    (tester) async {
      final repo = Jobs(),
          events = Events(),
          registry = JobWatchers(repo, events);
      final lease = registry.retain('j', 1, () => true, (_) {});
      await tester.pump();
      events.streams.last.addError(StateError('disconnect'));
      await tester.pump();
      await tester.pump(const Duration(seconds: 5));
      await tester.pump();
      expect(repo.gets, 2);
      registry.foreground(false);
      await tester.pump(const Duration(seconds: 30));
      expect(repo.gets, 2);
      registry.foreground(true);
      await tester.pump();
      expect(repo.gets, 3);
      lease.release();
      await tester.pump(const Duration(seconds: 30));
      expect(repo.gets, 3);
      registry.dispose();
    },
  );
}
