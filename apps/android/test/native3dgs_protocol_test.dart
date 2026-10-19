import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:native3dgs_flutter/native3dgs_view.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late StreamController<Object?> events;
  late Native3dgsController controller;
  final calls = <MethodCall>[];
  setUp(() {
    events = StreamController<Object?>.broadcast(sync: true);
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('native3dgs/methods'), (
          call,
        ) async {
          calls.add(call);
          return <String, Object?>{'ok': true, 'supported': true};
        });
    controller = Native3dgsController(
      viewId: 7,
      sessionId: 'session',
      events: events.stream,
    );
  });
  tearDown(() async {
    await controller.dispose();
    await events.close();
  });
  Map<String, Object?> state(
    int operation, {
    String session = 'session',
    int view = 7,
  }) => {
    'protocolVersion': 1,
    'viewId': view,
    'sessionId': session,
    'operationId': operation,
    'phase': 'Ready',
    'activeRequestId': 19,
    'requestId': 19,
    'sceneCount': 100,
    'surfaceGeneration': 1,
    'error': 0,
  };
  test('disposed controller drops late events', () async {
    await controller.dispose();
    events.add(state(0));
    expect(controller.state.phase, 'Empty');
  });
  test(
    'late operation and other view or session cannot replace current state',
    () async {
      await controller.open('content://models/a');
      await controller.open('content://models/b');
      events.add(state(1));
      events.add(state(2, session: 'old'));
      events.add(state(2, view: 8));
      expect(controller.state.phase, 'Loading');
      events.add(state(2));
      expect(controller.state.phase, 'Ready');
      expect(controller.state.activeRequestId, 19);
    },
  );
  test('close invalidates in flight open before native reply', () async {
    await controller.open('content://models/a');
    await controller.close();
    events.add(state(1));
    expect(controller.state.phase, 'Empty');
    expect((calls.last.arguments as Map)['operationId'], 2);
  });
  test('HTTP and file paths never cross the renderer boundary', () async {
    await expectLater(
      controller.open('https://example.org/model.ply'),
      throwsArgumentError,
    );
    expect(calls, isEmpty);
  });
  test('commands after disposal do not publish to closed streams', () async {
    await controller.dispose();
    final open = await controller.open('content://models/a');
    final close = await controller.close();
    expect(open.ok, isFalse);
    expect(close.ok, isFalse);
  });
  test('loading replacement keeps last visible scene metadata', () async {
    await controller.open('content://models/a');
    events.add(state(1));
    await controller.open('content://models/b');
    expect(controller.state.sceneCount, 100);
    expect(controller.state.activeRequestId, 19);
  });
}
