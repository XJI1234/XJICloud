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
  int _generation = -1;
  Future<void>? _initializing;
  ModelSource? _pending;
  int _pendingGeneration = -1;
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

  Future<void> initialize() => _initializing ??= session.initialize().then((_) {
    final source = _pending;
    _pending = null;
    if (source != null &&
        sessions.session != null &&
        _pendingGeneration == sessions.generation) {
      // Engine readiness must not wait for this source's download: a newer
      // open must be able to supersede it immediately.
      unawaited(session.open(source));
    }
  });
  Future<void> open(ModelSource source) async {
    if (sessions.session == null) return;
    final generation = sessions.generation;
    if (_initializing == null) {
      _pendingGeneration = generation;
      _pending = source;
      return;
    }
    await _initializing;
    if (sessions.session != null && generation == sessions.generation) {
      await session.open(source);
    }
  }

  Future<void> close() async {
    _pending = null;
    await session.close();
  }

  Future<void> cancel() => session.cancel();
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
    _initializing = null;
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(session.dispose());
    super.dispose();
  }
}
