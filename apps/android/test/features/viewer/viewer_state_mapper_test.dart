import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:native3dgs_flutter/native3dgs_view.dart';
import 'package:xjicloud_mobile/features/viewer/infrastructure/viewer_state_mapper.dart';
import 'package:xjicloud_mobile/features/viewer/infrastructure/native3dgs_engine_adapter.dart';
import 'package:xjicloud_mobile/features/viewer/domain/model_source.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_capabilities.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_engine_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'SDK states map explicitly without guessing an error numeric namespace',
    () {
      expect(
        mapViewerState(const Native3dgsState(phase: 'Loading')).phase,
        ViewerPhase.decoding,
      );
      expect(
        mapViewerState(const Native3dgsState(phase: 'Uploading')).phase,
        ViewerPhase.uploading,
      );
      expect(
        mapViewerState(
          const Native3dgsState(
            phase: 'Failed',
            error: 5,
            errorOrigin: 'State',
          ),
        ).phase,
        ViewerPhase.failed,
      );
      final ready = mapViewerState(
        const Native3dgsState(phase: 'Ready', requestId: 8, activeRequestId: 7),
      );
      expect(ready.requestId, 8);
      expect(ready.activeRequestId, 7);
    },
  );
  test('host tokens survive requestId zero response, early events and stale operation', () async {
    final events = StreamController<Object?>.broadcast(sync: true);
    final native = Native3dgsController(
      viewId: 7,
      sessionId: 'test',
      events: events.stream,
    );
    final engine = Native3dgsEngineAdapter();
    engine.attach(native);
    final seen = <ViewerEngineState>[];
    final sub = engine.states.listen(seen.add);
    int op = 0;
    Map<String, Object?> event(int operation, int request, int active) => {
      'protocolVersion': 1,
      'viewId': 7,
      'sessionId': 'test',
      'operationId': operation,
      'phase': 'Ready',
      'requestId': request,
      'activeRequestId': active,
    };
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('native3dgs/methods'), (
          call,
        ) async {
          if (call.method == 'initialize') {
            return {'ok': true, 'supported': true};
          }
          if (call.method == 'open') {
            op = (call.arguments as Map)['operationId'];
            events.add(event(op, 100 + op, 100 + op));
          }
          return {'ok': true, 'requestId': 0};
        });
    await engine.initialize();
    LocalModelRef ref(String name) => LocalModelRef(
      handle: 'content://private/$name',
      fileName: name,
      releaseResource: () async {},
    );
    final a = await engine.open(ref('A')) as Success<int>;
    expect(a.value, 1);
    final b = await engine.open(ref('B')) as Success<int>;
    expect(b.value, 2);
    expect(seen.last.activeRequestId, 2);
    events.add(event(op - 1, 101, 101));
    expect(seen.last.activeRequestId, 2);
    events.add(event(op, 102, 101));
    expect(seen.last.requestId, 2);
    expect(seen.last.activeRequestId, 1);
    await sub.cancel();
    await native.dispose();
    await events.close();
    await engine.dispose();
  });
}
