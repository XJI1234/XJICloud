import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:native3dgs_flutter/native3dgs_view.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/model_source.dart';
import '../domain/viewer_capabilities.dart';
import '../domain/viewer_engine_port.dart';
import 'viewer_state_mapper.dart';

/// Native widget and SDK classes stay at this infrastructure boundary.
final class Native3dgsEngineAdapter implements ViewerEnginePort {
  Native3dgsController? _native;
  var _attached = Completer<Native3dgsController>();
  final _states = StreamController<ViewerEngineState>.broadcast(sync: true);
  StreamSubscription<Native3dgsState>? _subscription;
  int _request = 0;
  final _nativeRequests = <int, int>{};
  @override
  int activeRequestId = 0;
  Future<Native3dgsResult>? _shutdown;
  bool _visible = true;
  bool _supported = false;
  @override
  Stream<ViewerEngineState> get states => _states.stream;
  Widget surface() => _NativeSurface(adapter: this);
  void attach(Native3dgsController controller) {
    _shutdown = null;
    _nativeRequests.clear();
    activeRequestId = 0;
    _native = controller;
    _subscription = controller.states.listen((state) {
      if (state.requestId == 0 &&
          state.phase != 'Failed' &&
          state.phase != 'Unsupported') {
        return;
      }
      final mapped = mapViewerState(state);
      if (state.requestId > 0) {
        _nativeRequests.putIfAbsent(state.requestId, () => _request);
      }
      activeRequestId = _nativeRequests[state.activeRequestId] ?? 0;
      _states.add(
        ViewerEngineState(
          mapped.phase,
          requestId: _nativeRequests[state.requestId] ?? _request,
          activeRequestId: activeRequestId,
        ),
      );
    });
    if (!_attached.isCompleted) _attached.complete(controller);
  }

  void detach() {
    unawaited(_subscription?.cancel());
    _subscription = null;
    _shutdown = _native?.shutdown();
    _native = null;
    _attached = Completer();
    _supported = false;
  }

  @override
  Future<Result<ViewerCapabilities>> initialize() async {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return const Success(ViewerCapabilities(supported: false));
    }
    final controller = await _attached.future;
    final result = await controller.initialize();
    if (!identical(_native, controller)) {
      return const Failure(DomainError('cancelled'));
    }
    _supported = result.ok && result.supported == true;
    if (_supported) await controller.setVisible(_visible);
    return Success(ViewerCapabilities(supported: _supported));
  }

  Result<void> _result(Native3dgsResult r) =>
      r.ok ? const Success(null) : const Failure(DomainError('viewer_engine'));
  @override
  Future<Result<int>> open(LocalModelRef model) async {
    if (!_supported || _native == null) {
      return const Failure(DomainError('viewer_unsupported'));
    }
    if (model.handle is! String) {
      return const Failure(DomainError('source_changed'));
    }
    final request = ++_request;
    final result = await _native!.open(model.handle as String);
    return result.ok
        ? Success(request)
        : const Failure(DomainError('viewer_engine'));
  }

  @override
  Future<Result<void>> cancel() async {
    if (_native == null) return const Success(null);
    final result = await _native!.cancel();
    final nativeActive = result.activeRequestId;
    if (nativeActive != null) {
      activeRequestId = nativeActive == 0
          ? 0
          : _nativeRequests.putIfAbsent(nativeActive, () => _request);
    }
    return _result(result);
  }

  @override
  Future<Result<void>> close() async {
    if (_shutdown case final closing?) return _result(await closing);
    if (_native == null || !_supported) return const Success(null);
    return _result(await _native!.close());
  }

  @override
  Future<Result<void>> visible(bool value) async {
    _visible = value;
    return _native == null || !_supported
        ? const Success(null)
        : _result(await _native!.setVisible(value));
  }

  @override
  Future<Result<void>> fullscreen(bool value) async {
    try {
      await SystemChrome.setEnabledSystemUIMode(
        value ? SystemUiMode.immersiveSticky : SystemUiMode.edgeToEdge,
      );
      return const Success(null);
    } on PlatformException {
      return const Failure(DomainError('viewer_engine'));
    }
  }

  @override
  Future<Result<void>> camera(
    ViewerCamera command, [
    List<double> values = const [],
  ]) async {
    if (_native == null || !_supported) {
      return const Failure(DomainError('viewer_unsupported'));
    }
    return _result(
      await _native!.camera(
        command == ViewerCamera.flip ? 'flipAxes' : command.name,
        command == ViewerCamera.flip ? [0, values.first, 0] : values,
      ),
    );
  }

  Future<void> dispose() async {
    await _subscription?.cancel();
    await _states.close();
  }
}

/// Paths only enter here from trusted private-store resolvers in create_container.
Future<String> privateModelUri(String path) async {
  try {
    final uri = await const MethodChannel('xjicloud/platform_files')
        .invokeMethod<String>('modelUri', {'path': path});
    if (uri == null || Uri.tryParse(uri)?.scheme != 'content') {
      throw const DomainError('storage');
    }
    return uri;
  } on PlatformException {
    throw const DomainError('storage');
  }
}

class _NativeSurface extends StatefulWidget {
  const _NativeSurface({required this.adapter});
  final Native3dgsEngineAdapter adapter;
  @override
  State<_NativeSurface> createState() => _NativeSurfaceState();
}

class _NativeSurfaceState extends State<_NativeSurface> {
  @override
  Widget build(BuildContext context) =>
      defaultTargetPlatform == TargetPlatform.android
      ? Native3dgsView(onCreated: widget.adapter.attach)
      : const SizedBox.expand();
  @override
  void dispose() {
    widget.adapter.detach();
    super.dispose();
  }
}
