import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../../identity/domain/session_access.dart';
import '../domain/selected_image.dart';
import '../domain/dataset_manifest.dart';
import '../domain/training_job.dart';
import '../domain/training_job_repository.dart';
import '../domain/training_output_port.dart';
import '../domain/object_storage_port.dart';
import '../domain/job_event_port.dart';
import '../domain/upload_checkpoint_port.dart';
import '../application/prepare_dataset.dart';
import '../application/submit_dataset.dart';
import '../application/resume_dataset.dart';
import '../application/watch_job.dart';
import '../application/mutate_job.dart';

/// Account-scoped owner of the fixed-target upload and shared foreground watchers.
final class UploadController extends ChangeNotifier {
  UploadController(
    this.repository,
    this.sources,
    ObjectStoragePort storage,
    this.checkpoints,
    JobEventPort events,
    this.sessions, {
    this.outputs,
  }) : submitter = SubmitDataset(repository, sources, storage, checkpoints),
       watchers = JobWatchers(repository, events);
  final TrainingJobRepository repository;
  final ImageSourcePort sources;
  final UploadCheckpointPort checkpoints;
  final SessionAccess sessions;
  final TrainingOutputPort? outputs;
  final SubmitDataset submitter;
  final JobWatchers watchers;
  String? userId, targetProjectId;
  int userGeneration = -1;
  DatasetManifest? draft;
  DatasetCheckpoint? checkpoint;
  DatasetTicket? _ticket;
  DomainError? error;
  bool busy = false, restoring = false, paused = false;
  bool _preparing = false, _foreground = true, _disposed = false;
  int sentBytes = 0, totalBytes = 0;
  Cancellation? _cancellation;
  Future<void> _operation = Future.value(), _restoreTask = Future.value();
  final _jobs = <String, List<TrainingJob>>{};
  final _projectRefs = <String, int>{};
  final _leases = <String, JobWatchLease>{};
  final _loading = <String>{};
  final _listErrors = <String, DomainError>{};
  final _versions = <String, int>{};
  final _projectEpoch = <String, int>{};
  final _mutating = <String>{};
  final _downloads = <String, Cancellation>{};
  final _downloadTasks = <String, Future<void>>{};
  final _downloadProjects = <String, String>{};
  final _localOutputs = <String, TrainingOutputRef>{};
  final outputBytes = <String, int>{};
  bool _current(int g) =>
      !_disposed &&
      g == userGeneration &&
      g == sessions.generation &&
      userId == sessions.session?.userId &&
      userId != null;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  List<TrainingJob> jobsFor(String p) => _jobs[p] ?? const [];
  bool loading(String p) => _loading.contains(p);
  DomainError? listError(String p) => _listErrors[p];
  bool mutating(String id) => _mutating.contains(id);
  bool downloading(String id) => _downloads.containsKey(id);
  TrainingOutputRef? outputFor(String id) => _localOutputs[id];
  void synchronize() {
    if (userGeneration == sessions.generation &&
        userId == sessions.session?.userId) {
      return;
    }
    final abandoned = checkpoint == null ? draft : null;
    if (abandoned != null) {
      for (final image in abandoned.files) {
        unawaited(sources.release(image.source));
      }
    }
    if (outputs != null) {
      for (final output in _localOutputs.values) {
        unawaited(outputs!.remove(output));
      }
    }
    pause();
    watchers.dispose();
    userGeneration = sessions.generation;
    userId = sessions.session?.userId;
    draft = null;
    checkpoint = null;
    _ticket = null;
    targetProjectId = null;
    error = null;
    busy = false;
    restoring = false;
    _preparing = false;
    paused = false;
    sentBytes = totalBytes = 0;
    _jobs.clear();
    _projectRefs.clear();
    _leases.clear();
    _loading.clear();
    _listErrors.clear();
    _versions.clear();
    _projectEpoch.clear();
    _mutating.clear();
    _downloads.clear();
    _downloadTasks.clear();
    _downloadProjects.clear();
    _localOutputs.clear();
    outputBytes.clear();
    _notify();
    if (userId != null) _restoreTask = _restore(userId!, userGeneration);
  }

  Future<void> _restore(String user, int g) async {
    restoring = true;
    _notify();
    final result = await checkpoints.read(user);
    if (!_current(g)) return;
    if (result case Success<DatasetCheckpoint?>(value: final state?)) {
      checkpoint = state;
      draft = state.manifest;
      targetProjectId = state.projectId;
      totalBytes = state.manifest.totalBytes;
      paused = true;
      error = DomainError(
        state.phase == DatasetUploadPhase.completed
            ? 'upload_completed_storage'
            : state.jobId == null
            ? 'dataset_create_uncertain'
            : 'dataset_restart_required',
      );
    } else if (result case Failure<DatasetCheckpoint?>(:final error)) {
      this.error = error;
    }
    restoring = false;
    _notify();
  }

  void foreground() {
    _foreground = true;
    watchers.foreground(true);
    final g = userGeneration;
    _operation.then<void>((_) {
      if (_current(g) && _foreground && checkpoint?.jobId != null && !busy) {
        resume();
      }
    }, onError: (Object _, StackTrace _) {});
  }

  void background() {
    _foreground = false;
    watchers.foreground(false);
    if (!_preparing) pause();
    for (final c in _downloads.values) {
      c.cancel();
    }
  }

  void pause() {
    _cancellation?.cancel();
    for (final c in _downloads.values) {
      c.cancel();
    }
    paused = checkpoint != null;
    _notify();
  }

  Future<void> choose(String projectId, {bool directory = false}) {
    if (busy ||
        restoring ||
        checkpoint != null ||
        draft != null ||
        userId == null) {
      return Future.value();
    }
    final g = userGeneration, c = Cancellation();
    _cancellation = c;
    targetProjectId = projectId;
    busy = true;
    _preparing = true;
    error = null;
    _notify();
    return _operation = () async {
      try {
        final result = await sources.choose(directory, c);
        if (result case Success<List<SelectedImage>?>(value: final selected?)) {
          if (!_current(g) || c.isCancelled) {
            for (final image in selected) {
              await sources.release(image);
            }
            return;
          }
          final prepared = prepareDataset(selected);
          if (prepared case Success<DatasetManifest>(:final value)) {
            draft = value;
            totalBytes = value.totalBytes;
          } else {
            for (final image in selected) {
              await sources.release(image);
            }
            if (_current(g)) {
              error = (prepared as Failure<DatasetManifest>).error;
            }
          }
        } else if (result case Failure<List<SelectedImage>?>(:final error)) {
          if (_current(g)) this.error = error;
        }
      } finally {
        if (_current(g)) {
          if (draft == null && checkpoint == null) {
            targetProjectId = null;
          }
          busy = false;
          _preparing = false;
          _notify();
        }
      }
    }();
  }

  Future<void> submit(String name) {
    if (!_foreground ||
        busy ||
        restoring ||
        checkpoint != null ||
        draft == null ||
        targetProjectId == null ||
        userId == null) {
      return Future.value();
    }
    if (name.trim().isEmpty || name.trim().length > 256) {
      error = const DomainError('dataset_validation');
      _notify();
      return Future.value();
    }
    final g = userGeneration,
        c = Cancellation(),
        manifest = draft!,
        project = targetProjectId!,
        user = userId!;
    _cancellation = c;
    busy = true;
    paused = false;
    error = null;
    _notify();
    return _operation = () async {
      try {
        final created = await submitter.create(
          user,
          project,
          name,
          manifest,
          g,
          () => _current(g),
          cancellation: c,
          onCheckpoint: (state) {
            if (_current(g)) {
              checkpoint = state;
              _notify();
            }
          },
        );
        if (!_current(g)) return;
        if (created case Success<DatasetTicket>(:final value)) {
          _ticket = value;
          if (!c.isCancelled) await _run(g, c, resume: false);
        } else {
          error = (created as Failure<DatasetTicket>).error;
          if (checkpoint?.jobId == null) await refresh(project);
        }
      } finally {
        if (_current(g)) {
          busy = false;
          paused = c.isCancelled;
          _notify();
        }
      }
    }();
  }

  Future<void> resume({bool retryCompletion = false}) {
    if (!_foreground ||
        busy ||
        restoring ||
        checkpoint == null ||
        userId == null) {
      return Future.value();
    }
    final g = userGeneration, c = Cancellation();
    _cancellation = c;
    busy = true;
    paused = false;
    error = null;
    _notify();
    return _operation = () async {
      try {
        await _run(g, c, resume: true, retryCompletion: retryCompletion);
      } finally {
        if (_current(g)) {
          busy = false;
          paused = c.isCancelled;
          _notify();
        }
      }
    }();
  }

  Future<void> _run(
    int g,
    Cancellation c, {
    required bool resume,
    bool retryCompletion = false,
  }) async {
    final state = checkpoint;
    if (state == null) return;
    void changed(DatasetCheckpoint next) {
      if (_current(g)) {
        checkpoint = next;
        _notify();
      }
    }

    void progress(int n, int total) {
      if (_current(g) && !c.isCancelled) {
        sentBytes = n;
        totalBytes = total;
        _notify();
      }
    }

    final result = resume
        ? await ResumeDataset(submitter)(
            state,
            _ticket,
            g,
            c,
            () => _current(g),
            retryCompletion: retryCompletion,
            onCheckpoint: changed,
            onProgress: progress,
          )
        : await submitter.run(
            state,
            _ticket!,
            g,
            c,
            () => _current(g),
            onCheckpoint: changed,
            onProgress: progress,
          );
    if (!_current(g)) return;
    if (result case Failure<TrainingJob>(:final error)) {
      this.error = error;
      if (error.code == 'signature_expired' ||
          error.code == 'dataset_restart_required') {
        _ticket = null;
      }
    } else {
      final job = (result as Success<TrainingJob>).value;
      _updateJob(job);
      _ticket = null;
      final removed = await checkpoints.remove(state.userId);
      if (!_current(g)) return;
      if (removed is Failure<void>) {
        error = removed.error;
        return;
      }
      checkpoint = null;
      draft = null;
      targetProjectId = null;
      for (final file in state.manifest.files) {
        final released = await sources.release(file.source);
        if (released is Failure<void> && _current(g)) error = released.error;
      }
      if (_current(g)) await refresh(state.projectId);
    }
  }

  void Function() retainProject(String project) {
    final g = userGeneration;
    _projectRefs.update(project, (n) => n + 1, ifAbsent: () => 1);
    _reconcileWatches(project, g);
    scheduleMicrotask(() {
      if (_current(g) && _projectRefs.containsKey(project)) refresh(project);
    });
    var released = false;
    return () {
      if (released) return;
      released = true;
      if (!_current(g)) return;
      final n = (_projectRefs[project] ?? 1) - 1;
      if (n <= 0) {
        _projectRefs.remove(project);
        for (final job in jobsFor(project)) {
          _leases.remove(job.id)?.release();
        }
      } else {
        _projectRefs[project] = n;
      }
    };
  }

  void _reconcileWatches(String project, int g) {
    if (!_projectRefs.containsKey(project) || !_current(g)) return;
    for (final job in jobsFor(project)) {
      _leases.putIfAbsent(
        job.id,
        () => watchers.retain(
          job.id,
          g,
          () => _current(g),
          _updateJob,
          onError: (error) {
            if (_current(g)) {
              _listErrors[project] = error;
              _notify();
            }
          },
        ),
      );
    }
  }

  void _updateJob(TrainingJob job) {
    final jobs = List.of(jobsFor(job.projectId)),
        index = jobs.indexWhere((j) => j.id == job.id);
    if (index < 0) {
      jobs.insert(0, job);
    } else {
      jobs[index] = job;
    }
    _jobs[job.projectId] = List.unmodifiable(jobs);
    _versions.update(job.id, (v) => v + 1, ifAbsent: () => 1);
    _notify();
  }

  Future<void> refresh(String project) async {
    final g = userGeneration,
        epoch = _projectEpoch[project] ?? 0,
        versions = Map.of(_versions);
    if (!_current(g) || _loading.contains(project)) return;
    _loading.add(project);
    _listErrors.remove(project);
    _notify();
    final result = await repository.list(project, g);
    if (!_current(g)) return;
    _loading.remove(project);
    if (epoch != (_projectEpoch[project] ?? 0)) {
      _notify();
      return;
    }
    if (result case Success<List<TrainingJob>>(:final value)) {
      final old = {for (final job in jobsFor(project)) job.id: job};
      _jobs[project] = [
        for (final job in value)
          if (versions[job.id] != _versions[job.id] && old[job.id] != null)
            old[job.id]!
          else
            job,
      ];
      for (final id in old.keys.where((id) => !value.any((j) => j.id == id))) {
        _leases.remove(id)?.release();
      }
      _reconcileWatches(project, g);
    } else if (result case Failure<List<TrainingJob>>(:final error)) {
      _listErrors[project] = error;
    }
    _notify();
  }

  Future<void> resolveCandidate(String id) async {
    final state = checkpoint, g = userGeneration;
    if (state == null || state.jobId != null || busy) return;
    busy = true;
    _notify();
    try {
      final result = await repository.get(id, g);
      if (!_current(g)) return;
      if (result case Success<TrainingJob>(:final value)
          when value.projectId == state.projectId) {
        final next = state.copy(
          jobId: value.id,
          phase: DatasetUploadPhase.uploading,
        );
        final saved = await checkpoints.write(next);
        if (!_current(g)) return;
        if (saved is Success<void>) {
          checkpoint = next;
          error = const DomainError('dataset_restart_required');
        } else {
          error = (saved as Failure<void>).error;
        }
      } else {
        error = result is Failure<TrainingJob>
            ? result.error
            : const DomainError('forbidden');
      }
    } finally {
      if (_current(g)) {
        busy = false;
        _notify();
      }
    }
  }

  /// Called only after the user has checked the authoritative candidate list.
  Future<void> confirmNoCreatedJob() async {
    if (checkpoint?.jobId != null || checkpoint == null || busy) return;
    final g = userGeneration, result = await checkpoints.remove(userId!);
    if (!_current(g)) return;
    if (result is Success<void>) {
      checkpoint = null;
      _ticket = null;
      error = null;
    } else {
      error = (result as Failure<void>).error;
    }
    _notify();
  }

  Future<void> restart() async {
    if (busy || checkpoint?.jobId == null) return;
    final name = checkpoint!.name;
    final cleared = await discard(keepDraft: true, onlyUploading: true);
    if (cleared && draft != null) await submit(name);
  }

  Future<bool> discard({
    bool keepDraft = false,
    bool onlyUploading = false,
  }) async {
    if (busy || restoring || userId == null) return false;
    final g = userGeneration, state = checkpoint, manifest = draft;
    busy = true;
    error = null;
    _notify();
    try {
      if (state?.jobId != null &&
          (state!.phase != DatasetUploadPhase.completed || onlyUploading)) {
        final actual = await repository.get(state.jobId!, g);
        if (!_current(g)) return false;
        if (actual case Failure<TrainingJob>(:final error)
            when error.code != 'not_found') {
          this.error = error;
          return false;
        }
        if (actual case Success<TrainingJob>(:final value)) {
          if (onlyUploading && value.status != JobStatus.uploading) {
            _updateJob(value);
            error = const DomainError('job_state');
            return false;
          }
          if (value.status != JobStatus.completed &&
              value.status != JobStatus.cancelled) {
            final cancelled = await repository.cancel(value.id, g);
            if (!_current(g)) return false;
            if (cancelled case Failure<TrainingJob>(:final error)) {
              this.error = error;
              return false;
            }
            _updateJob((cancelled as Success<TrainingJob>).value);
          }
        }
      } else if (state != null && state.jobId == null) {
        error = const DomainError('dataset_create_uncertain');
        return false;
      }
      if (state != null) {
        final removed = await checkpoints.remove(state.userId);
        if (!_current(g)) return false;
        if (removed case Failure<void>(:final error)) {
          this.error = error;
          return false;
        }
      }
      checkpoint = null;
      _ticket = null;
      paused = false;
      if (!keepDraft) {
        draft = null;
        targetProjectId = null;
        if (manifest != null) {
          for (final file in manifest.files) {
            final result = await sources.release(file.source);
            if (!_current(g)) return false;
            if (result case Failure<void>(:final error)) {
              this.error = error;
            }
          }
        }
      }
      return true;
    } finally {
      if (_current(g)) {
        busy = false;
        _notify();
      }
    }
  }

  Future<void> cancelJob(TrainingJob job) async {
    final g = userGeneration;
    if (!_current(g) || _mutating.contains(job.id)) return;
    _mutating.add(job.id);
    _notify();
    try {
      if (checkpoint?.jobId == job.id) {
        pause();
        await _operation;
        if (!_current(g)) return;
      }
      final result = await MutateJob(repository).cancel(job, g);
      if (!_current(g)) return;
      if (result case Success<TrainingJob>(:final value)) {
        _updateJob(value);
        watchers.refresh(job.id, g);
      } else {
        error = (result as Failure<TrainingJob>).error;
      }
    } finally {
      if (_current(g)) {
        _mutating.remove(job.id);
        _notify();
      }
    }
  }

  Future<void> deleteJob(TrainingJob job) async {
    final g = userGeneration;
    if (!_current(g) || _mutating.contains(job.id)) return;
    _mutating.add(job.id);
    _notify();
    try {
      _downloads[job.id]?.cancel();
      await _downloadTasks[job.id];
      if (!_current(g)) return;
      final result = await MutateJob(repository).delete(job, g);
      if (!_current(g)) return;
      if (result is Success<void>) {
        _projectEpoch.update(job.projectId, (v) => v + 1, ifAbsent: () => 1);
        _jobs[job.projectId] = jobsFor(job.projectId)
            .where((j) => j.id != job.id)
            .toList();
        _leases.remove(job.id)?.release();
        final output = _localOutputs.remove(job.id);
        if (output != null) await outputs?.remove(output);
      } else {
        error = (result as Failure<void>).error;
      }
    } finally {
      if (_current(g)) {
        _mutating.remove(job.id);
        _notify();
      }
    }
  }

  Future<void> downloadOutput(TrainingJob job) {
    if (outputs == null ||
        userId == null ||
        _downloads.containsKey(job.id) ||
        job.status != JobStatus.completed) {
      return Future.value();
    }
    final g = userGeneration, user = userId!, c = Cancellation();
    _downloads[job.id] = c;
    _downloadProjects[job.id] = job.projectId;
    error = null;
    _notify();
    return _downloadTasks[job.id] = () async {
      try {
        var current = job;
        if (current.downloadUrl == null) {
          final full = await repository.get(job.id, g);
          if (!_current(g)) return;
          if (full case Success<TrainingJob>(:final value)) {
            current = value;
          } else {
            error = (full as Failure<TrainingJob>).error;
            return;
          }
        }
        for (var attempt = 0; attempt < 2; attempt++) {
          if (!_current(g) || c.isCancelled) return;
          if (current.downloadUrl == null) {
            error = const DomainError('output_unavailable');
            return;
          }
          final result = await outputs!.download(
            user,
            job.id,
            current.downloadUrl!,
            c,
            (n, total) {
              if (_current(g)) {
                outputBytes[job.id] = n;
                _notify();
              }
            },
          );
          if (!_current(g)) {
            if (result is Success<TrainingOutputRef>) {
              await outputs!.remove(result.value);
            }
            return;
          }
          if (result case Success<TrainingOutputRef>(:final value)) {
            final old = _localOutputs[job.id];
            _localOutputs[job.id] = value;
            if (old != null) await outputs!.remove(old);
            return;
          }
          final failure = (result as Failure<TrainingOutputRef>).error;
          if (failure.code != 'signature_expired') {
            error = failure;
            return;
          }
          if (attempt == 1) {
            error = const DomainError('output_url_expired');
            return;
          }
          final fresh = await repository.get(job.id, g);
          if (!_current(g)) return;
          if (fresh case Success<TrainingJob>(:final value)) {
            current = value;
            _updateJob(value);
          } else {
            error = (fresh as Failure<TrainingJob>).error;
            return;
          }
        }
      } finally {
        if (_current(g)) {
          _downloads.remove(job.id);
          _downloadTasks.remove(job.id);
          _downloadProjects.remove(job.id);
          outputBytes.remove(job.id);
          _notify();
        }
      }
    }();
  }

  void cancelDownload(String id) => _downloads[id]?.cancel();
  Future<Result<void>> stopProjectTransfers(String project) async {
    final g = userGeneration;
    await _restoreTask;
    if (!_current(g)) return const Failure(DomainError('cancelled'));
    if (targetProjectId == project) {
      _cancellation?.cancel();
      await _operation;
      if (!_current(g)) return const Failure(DomainError('cancelled'));
      if (!await discard()) {
        return Failure(error ?? const DomainError('request'));
      }
    }
    final ids = _downloadProjects.entries
        .where((e) => e.value == project)
        .map((e) => e.key)
        .toList();
    for (final id in ids) {
      _downloads[id]?.cancel();
    }
    for (final id in ids) {
      await _downloadTasks[id];
    }
    return _current(g)
        ? const Success(null)
        : const Failure(DomainError('cancelled'));
  }

  @override
  void dispose() {
    _disposed = true;
    if (checkpoint == null && draft != null) {
      for (final image in draft!.files) {
        unawaited(sources.release(image.source));
      }
    }
    _cancellation?.cancel();
    for (final c in _downloads.values) {
      c.cancel();
    }
    watchers.dispose();
    super.dispose();
  }
}
