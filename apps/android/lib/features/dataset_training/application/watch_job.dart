import 'dart:async';

import '../../../shared/domain/cancellation.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/job_event_port.dart';
import '../domain/job_policy.dart';
import '../domain/training_job.dart';
import '../domain/training_job_repository.dart';

final class JobWatchLease {
  JobWatchLease(this._release);
  void Function()? _release;
  void release() {
    _release?.call();
    _release = null;
  }
}

/// One subscription per account generation/job, shared by home and detail.
final class JobWatchers {
  JobWatchers(this.repository, this.events);
  final TrainingJobRepository repository;
  final JobEventPort events;
  final _watches = <String, _WatchJob>{};
  bool _foreground = true;
  JobWatchLease retain(
    String id,
    int generation,
    bool Function() current,
    void Function(TrainingJob) onChange, {
    void Function(DomainError)? onError,
  }) {
    final key = '$generation:$id';
    final watch = _watches.putIfAbsent(
      key,
      () => _WatchJob(repository, events, id, generation, current),
    );
    final observer = _Observer(onChange, onError);
    watch.observers.add(observer);
    if (watch.snapshot != null) onChange(watch.snapshot!);
    if (_foreground) watch.start();
    return JobWatchLease(() {
      watch.observers.remove(observer);
      if (watch.observers.isEmpty) {
        watch.stop();
        _watches.remove(key);
      }
    });
  }

  void foreground(bool value) {
    _foreground = value;
    for (final w in _watches.values) {
      value ? w.start() : w.stop();
    }
  }

  void refresh(String id, int generation) =>
      _watches['$generation:$id']?.refresh();
  void dispose() {
    for (final w in _watches.values) {
      w.stop();
    }
    _watches.clear();
  }
}

final class _Observer {
  _Observer(this.change, this.error);
  final void Function(TrainingJob) change;
  final void Function(DomainError)? error;
}

final class _WatchJob {
  _WatchJob(
    this.repository,
    this.events,
    this.id,
    this.generation,
    this.current,
  );
  final TrainingJobRepository repository;
  final JobEventPort events;
  final String id;
  final int generation;
  final bool Function() current;
  final observers = <_Observer>[];
  TrainingJob? snapshot;
  JobProgress? _lastEvent;
  Timer? _timer;
  Cancellation? _connection;
  StreamSubscription<JobProgress>? _subscription;
  int _revision = 0, _request = 0, _epoch = 0;
  bool _active = false, _degraded = false, _finished = false;
  bool get valid => _active && current() && observers.isNotEmpty;
  void start() {
    if (_active) return;
    _active = true;
    _finished = false;
    _epoch++;
    refresh();
    if (snapshot?.status != JobStatus.cancelled) _connect();
    _schedule();
  }

  void stop() {
    _active = false;
    _epoch++;
    _timer?.cancel();
    _timer = null;
    _closeEvents();
  }

  void _emit() {
    if (valid && snapshot != null) {
      for (final o in List.of(observers)) {
        o.change(snapshot!);
      }
    }
  }

  void _closeEvents() {
    final connection = _connection;
    _connection = null;
    connection?.cancel();
    _subscription?.cancel();
    _subscription = null;
  }

  void _schedule() {
    _timer?.cancel();
    if (!valid || _finished) return;
    _timer = Timer(Duration(seconds: _degraded ? 5 : 15), () {
      refresh();
      _schedule();
    });
  }

  void _connect() {
    if (!valid || _connection != null || _finished) return;
    final token = Cancellation();
    _connection = token;
    void failed([Object? error]) {
      if (!valid || _connection != token) return;
      _closeEvents();
      _degraded = true;
      _schedule();
      if (error is DomainError) {
        for (final o in List.of(observers)) {
          o.error?.call(error);
        }
      }
    }

    _subscription = events
        .subscribe(id, generation, token)
        .listen(
          (event) {
            if (!valid || _connection != token || event.jobId != id) return;
            _revision++;
            _lastEvent = event;
            final wasDegraded = _degraded;
            _degraded = false;
            snapshot = snapshot?.progressUpdate(event);
            _emit();
            if (event.status == JobStatus.cancelled) {
              _closeEvents();
              _schedule();
            } else if (needsTerminalSnapshot(event.status)) {
              refresh();
            } else if (wasDegraded) {
              _schedule();
            }
          },
          onError: failed,
          onDone: () => failed(),
        );
  }

  Future<void> refresh() async {
    if (!valid || _finished) return;
    final epoch = _epoch, revision = _revision, request = ++_request;
    final result = await repository.get(id, generation);
    if (!valid || epoch != _epoch || request != _request) return;
    if (result case Failure<TrainingJob>(:final error)) {
      _degraded = true;
      _schedule();
      for (final o in List.of(observers)) {
        o.error?.call(error);
      }
      return;
    }
    final full = (result as Success<TrainingJob>).value;
    if (full.id != id) return;
    if (revision != _revision) {
      if (snapshot == null && _lastEvent != null) {
        snapshot = full.progressUpdate(_lastEvent!);
        _emit();
      }
      return;
    }
    // A terminal event has no artifact/error details. Keep querying until its full snapshot arrives.
    if (_lastEvent != null &&
        needsTerminalSnapshot(_lastEvent!.status) &&
        full.status != _lastEvent!.status) {
      _degraded = true;
      _schedule();
      return;
    }
    snapshot = full;
    _lastEvent = null;
    _emit();
    if (needsTerminalSnapshot(full.status)) {
      _finished = true;
      _timer?.cancel();
      _closeEvents();
    } else if (full.status == JobStatus.cancelled) {
      _degraded = false;
      _closeEvents();
      _schedule();
    } else {
      // CANCELLED may be overwritten by the worker. Reconnect exactly once.
      if (_connection == null) _connect();
      _schedule();
    }
  }
}
