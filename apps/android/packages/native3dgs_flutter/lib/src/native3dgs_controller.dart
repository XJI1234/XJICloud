import 'dart:async';

import 'package:flutter/services.dart';

import 'native3dgs_state.dart';

class Native3dgsController {
  Native3dgsController({
    required this.viewId,
    required this.sessionId,
    Stream<Object?>? events,
  }) {
    _subscription =
        (events ??
                EventChannel('native3dgs/events/$viewId')
                    .receiveBroadcastStream())
            .listen(
              _event,
              onError: (Object error) {
                if (!_disposed) {
                  _publish(
                    const Native3dgsState(
                      phase: 'Failed',
                      message: '状态通道断开，请退出后重试。',
                    ),
                  );
                }
              },
            );
  }
  static const _channel = MethodChannel('native3dgs/methods');
  final int viewId;
  final String sessionId;
  int _operation = 0;
  bool _disposed = false;
  late final StreamSubscription<Object?> _subscription;
  final _states = StreamController<Native3dgsState>.broadcast(sync: true);
  Native3dgsState _state = const Native3dgsState();
  Native3dgsState get state => _state;
  Stream<Native3dgsState> get states => _states.stream;
  void _publish(Native3dgsState value) {
    if (_disposed) return;
    _state = value;
    _states.add(value);
  }

  void _event(Object? event) {
    if (_disposed ||
        event is! Map ||
        event['protocolVersion'] != 1 ||
        event['viewId'] != viewId ||
        event['sessionId'] != sessionId ||
        event['operationId'] != _operation) {
      return;
    }
    _publish(Native3dgsState.fromMap(event));
  }

  Future<Native3dgsResult> _invoke(
    String method, [
    Map<String, Object?> extra = const {},
  ]) async {
    if (_disposed) return const Native3dgsResult(ok: false, message: '查看器已退出。');
    final operation = _operation;
    try {
      final data = await _channel.invokeMapMethod<String, Object?>(method, {
        'protocolVersion': 1,
        'viewId': viewId,
        'sessionId': sessionId,
        'operationId': operation,
        ...extra,
      });
      if (method != 'dispose' && (_disposed || operation != _operation)) {
        return const Native3dgsResult(ok: false, message: '操作已被替代。');
      }
      final result = Native3dgsResult(
        ok: data?['ok'] == true,
        message: data?['message'] as String?,
        requestId: (data?['requestId'] as num?)?.toInt(),
        activeRequestId: (data?['activeRequestId'] as num?)?.toInt(),
        uri: data?['uri'] as String?,
        supported: data?['supported'] as bool?,
        error: (data?['error'] as num?)?.toInt() ?? 0,
        errorOrigin: data?['errorOrigin'] as String?,
      );
      if (!result.ok) {
        _publish(
          _state.withPhase(
            'Failed',
            message: result.message,
            error: result.error,
            errorOrigin: result.errorOrigin,
          ),
        );
      }
      return result;
    } on PlatformException catch (error) {
      final result = Native3dgsResult(
        ok: false,
        message: '原生操作失败 (${error.code})，请退出后重试。',
      );
      if (!_disposed && operation == _operation) {
        _publish(Native3dgsState(phase: 'Failed', message: result.message));
      }
      return result;
    } on MissingPluginException {
      return const Native3dgsResult(ok: false, message: '当前平台未安装原生查看器。');
    }
  }

  Future<Native3dgsResult> initialize() async {
    final result = await _invoke('initialize');
    if (!_disposed) {
      _publish(
        Native3dgsState(
          phase: result.supported == true ? 'Empty' : 'Unsupported',
          supported: result.supported,
          message: result.message,
          error: result.error,
          errorOrigin: result.errorOrigin,
        ),
      );
    }
    return result;
  }

  Future<Native3dgsResult> setVisible(bool visible) =>
      _invoke('visibility', {'visible': visible});

  Future<Native3dgsResult> pickFile() => _invoke('pickFile');
  Future<Native3dgsResult> open(String uri) async {
    if (Uri.tryParse(uri)?.scheme != 'content') {
      throw ArgumentError.value(uri, 'uri', 'Requires local content URI');
    }
    _operation++;
    _publish(_state.withPhase('Loading', requestId: 0));
    return _invoke('open', {'uri': uri});
  }

  Future<Native3dgsResult> cancel() {
    final target = _operation++;
    return _invoke('cancel', {'targetOperationId': target});
  }

  Future<Native3dgsResult> close() {
    _operation++;
    _publish(const Native3dgsState());
    return _invoke('close');
  }

  Future<Native3dgsResult> camera(
    String command, [
    List<double> values = const [],
  ]) => _invoke('camera', {'command': command, 'values': values});
  Future<Native3dgsResult>? _disposing;

  /// Repeated callers share the actual native closeAndWait acknowledgement.
  Future<Native3dgsResult> shutdown() => _disposing ??= (() async {
    final closing = _invoke('dispose');
    _disposed = true;
    await _subscription.cancel();
    final result = await closing;
    await _states.close();
    return result;
  })();
  Future<void> dispose() async {
    await shutdown();
  }
}
