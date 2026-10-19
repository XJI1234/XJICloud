import 'dart:async';

import '../../../shared/domain/cancellation.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/model_source.dart';
import '../domain/model_source_port.dart';
import '../domain/viewer_engine_port.dart';
import '../domain/viewer_capabilities.dart';
import 'open_model.dart';

/// Host transaction owner. Files outlive decoder work and the active scene.
final class ViewerSession {
  ViewerSession(this.sources, this.engine, {required this.changed});
  final ModelSourcePort sources;
  final ViewerEnginePort engine;
  final void Function() changed;
  LocalModelRef? active, _candidate;
  ViewerPhase phase = ViewerPhase.empty;
  DomainError? error;
  int bytes = 0;
  int? total;
  bool free = false, flipped = false;
  bool _supported = false;
  int _epoch = 0, _request = 0, _candidateEpoch = 0, _activeRequest = 0;
  Cancellation? _download;
  StreamSubscription<ViewerEngineState>? _subscription;
  final _early = <ViewerEngineState>[];
  Future<void> _tail = Future.value();
  Future<T> _serial<T>(Future<T> Function() action) {
    final next = _tail.then((_) => action());
    _tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  void _fail(DomainError failure) {
    error = failure;
    phase = ViewerPhase.failed;
    changed();
  }

  Future<void> initialize() async {
    _subscription ??= engine.states.listen(_event);
    final epoch = _epoch;
    final initialized = await engine.initialize();
    if (epoch != _epoch) return;
    switch (initialized) {
      case Success<ViewerCapabilities>(:final value):
        _supported = value.supported;
        phase = value.supported ? ViewerPhase.empty : ViewerPhase.unsupported;
      case Failure<ViewerCapabilities>(:final error):
        _fail(error);
    }
    changed();
  }

  void _event(ViewerEngineState state) {
    if (_candidate == null) {
      if (phase == ViewerPhase.downloading || phase == ViewerPhase.closing) {
        return;
      }
      if (active != null &&
          (state.activeRequestId == _activeRequest ||
              state.phase == ViewerPhase.failed)) {
        if (state.phase == ViewerPhase.recovering ||
            state.phase == ViewerPhase.ready) {
          phase = state.phase;
        }
        if (state.phase == ViewerPhase.failed) {
          phase = ViewerPhase.failed;
          error = const DomainError('viewer_engine');
        }
        changed();
      }
      return;
    }
    if (_candidateEpoch != _epoch) return;
    if (_request == 0) {
      _early.add(state);
      return;
    }
    if (state.requestId != _request) return;
    if (state.phase == ViewerPhase.ready) {
      if (state.activeRequestId != _request) return;
      final old = active;
      active = _candidate;
      _activeRequest = _request;
      _candidate = null;
      phase = ViewerPhase.ready;
      error = null;
      if (old != null) unawaited(_release(old).then((_) {}));

      // Unknown server viewer-config has no coordinate contract: fit, never write.
      unawaited(camera(ViewerCamera.fit));
    } else {
      phase = state.phase;
      if (phase == ViewerPhase.failed) {
        error = const DomainError('viewer_engine');
      }
    }
    changed();
  }

  Future<bool> _release(LocalModelRef ref) async {
    try {
      await ref.release();
      return true;
    } on DomainError catch (e) {
      error = e;
      changed();
      return false;
    }
  }

  Future<bool> _cancelCandidate() async {
    if (_candidate == null) return true;
    final result = await engine.cancel();
    if (result case Failure<void>(:final error)) {
      _fail(error);
      return false;
    }
    final old = _candidate;
    final candidateRequest = _request;
    final mayBeActive =
        candidateRequest > 0 && engine.activeRequestId == candidateRequest;
    _candidate = null;
    _request = 0;
    _early.clear();
    if (old != null) {
      if (mayBeActive) {
        // The native first frame won before cancellation. A cannot be claimed as
        // visible anymore: acknowledge B and retain its lease even if C fails.
        final previous = active;
        active = old;
        _activeRequest = candidateRequest;
        if (previous != null) await _release(previous);
        changed();
      } else {
        await _release(old);
      }
    }
    return true;
  }

  Future<void> open(ModelSource source) async {
    if (!_supported) {
      phase = ViewerPhase.unsupported;
      changed();
      return;
    }
    final epoch = ++_epoch;
    _download?.cancel();
    final token = _download = Cancellation();
    final cancelled = await _serial(_cancelCandidate);
    if (!cancelled || epoch != _epoch) return;
    phase = ViewerPhase.downloading;
    error = null;
    bytes = 0;
    total = null;
    changed();
    final result = await OpenModel(sources)(
      source,
      token,
      onProgress: (b, t) {
        if (epoch == _epoch) {
          bytes = b;
          total = t;
          changed();
        }
      },
    );
    if (result case Failure<LocalModelRef>(:final error)) {
      if (epoch == _epoch) {
        if (error.code == 'cancelled') {
          phase = active == null ? ViewerPhase.empty : ViewerPhase.ready;
          changed();
        } else {
          _fail(error);
        }
      }
      return;
    }
    final ref = (result as Success<LocalModelRef>).value;
    if (epoch != _epoch || token.isCancelled) {
      await _release(ref);
      return;
    }
    await _serial(() async {
      if (epoch != _epoch) {
        await _release(ref);
        return;
      }
      _candidate = ref;
      _candidateEpoch = epoch;
      _request = 0;
      _early.clear();
      phase = ViewerPhase.decoding;
      changed();
      final opened = await engine.open(ref);
      if (opened case Failure<int>(:final error)) {
        _fail(error);
        return;
      }
      _request = (opened as Success<int>).value;
      if (epoch != _epoch) return;
      for (final event in List<ViewerEngineState>.of(_early)) {
        _event(event);
      }
      _early.clear();
    });
  }

  Future<void> cancel() async {
    ++_epoch;
    _download?.cancel();
    await _serial(() async {
      if (await _cancelCandidate()) {
        error = null;
        phase = active == null ? ViewerPhase.empty : ViewerPhase.ready;
        changed();
      }
    });
  }

  Future<void> close() async {
    ++_epoch;
    _download?.cancel();
    phase = ViewerPhase.closing;
    changed();
    await _serial(() async {
      final result = await engine.close();
      if (result case Failure<void>(:final error)) {
        _fail(error);
        return;
      }
      final refs = [?active, ?_candidate];
      active = null;
      _activeRequest = 0;
      _candidate = null;
      _request = 0;
      _early.clear();
      var cleaned = true;
      for (final ref in refs) {
        if (!await _release(ref)) cleaned = false;
      }
      phase = ViewerPhase.empty;
      if (cleaned) error = null;
      free = false;
      flipped = false;
      changed();
    });
  }

  Future<void> camera(
    ViewerCamera command, [
    List<double> values = const [],
  ]) async {
    final result = await engine.camera(command, values);
    if (result case Failure<void>(:final error)) {
      this.error = error;
      changed();
      return;
    }
    if (command == ViewerCamera.fixed) free = false;
    if (command == ViewerCamera.free) free = true;
    if (command == ViewerCamera.flip) flipped = values.first == 1;
    changed();
  }

  Future<void> dispose() async {
    await close();
    await _subscription?.cancel();
  }
}
