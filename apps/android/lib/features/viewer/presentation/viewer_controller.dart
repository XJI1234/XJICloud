import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../identity/domain/session_access.dart';
import '../application/viewer_session.dart';
import '../domain/model_source.dart';
import '../domain/model_source_port.dart';
import '../domain/viewer_engine_port.dart';
import '../domain/viewer_capabilities.dart';
import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';

final class ViewerController extends ChangeNotifier {
  ViewerController(
    ModelSourcePort sources,
    ViewerEnginePort engine,
    this.sessions,
  ) {
    session = ViewerSession(sources, engine, changed: _changed);
  }
  final SessionAccess sessions;
  late final ViewerSession session;
  bool fullscreen = false, visible = false, _disposed = false;
  bool surfaceRequested = false;
  int _generation = -1;
  Future<void>? _initializing;
  ModelSource? _pending;
  int _pendingGeneration = -1;
  int _openEpoch = 0, _pendingEpoch = -1;
  int _surfaceEpoch = 0;
  void _changed() {
    if (!_disposed) notifyListeners();
  }

  void synchronize() {
    if (_generation == sessions.generation) return;
    _generation = sessions.generation;
    _pending = null;
    setFullscreen(false);
    unawaited(close());
  }

  Future<void> initialize() {
    final surfaceEpoch = _surfaceEpoch;
    return _initializing ??= session.initialize().then((_) {
      if (_disposed || surfaceEpoch != _surfaceEpoch) return;
      final source = _pending;
      _pending = null;
      if (source != null &&
          _pendingEpoch == _openEpoch &&
          sessions.session != null &&
          _pendingGeneration == sessions.generation) {
        // Engine readiness must not wait for this source's download: a newer
        // open must be able to supersede it immediately.
        unawaited(session.open(source));
      }
    });
  }

  Future<void> open(ModelSource source) async {
    if (sessions.session == null) return;
    final generation = sessions.generation;
    final epoch = ++_openEpoch;
    if (_initializing == null) {
      _pendingGeneration = generation;
      _pendingEpoch = epoch;
      _pending = source;
      surfaceRequested = true;
      _changed();
      return;
    }
    await _initializing;
    if (!_disposed &&
        epoch == _openEpoch &&
        sessions.session != null &&
        generation == sessions.generation) {
      await session.open(source);
    }
  }

  Future<void> close() async {
    ++_openEpoch;
    _pending = null;
    if (fullscreen) setFullscreen(false);
    await session.close();
  }

  Future<void> cancel() {
    ++_openEpoch;
    _pending = null;
    return session.cancel();
  }

  Future<void> camera(ViewerCamera c, [List<double> values = const []]) =>
      session.camera(c, values);
  Future<void> setVisible(bool value) async {
    visible = value;
    final result = await session.engine.visible(value);
    if (result case Failure<void>(:final error)) {
      session.error = error;
      _changed();
    }
  }

  void setFullscreen(bool value) {
    fullscreen = value;
    unawaited(
      session.engine.fullscreen(value).then((result) {
        if (result case Failure<void>(:final error)) {
          session.error = error;
          _changed();
        }
      }),
    );
    _changed();
  }

  void reportError(DomainError error) {
    session.error = error;
    _changed();
  }

  /// Called when the account route is removed, after invalidating source work.
  void surfaceRemoved() {
    ++_surfaceEpoch;
    _initializing = null;
    surfaceRequested = false;
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(session.dispose());
    super.dispose();
  }
}
