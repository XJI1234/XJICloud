import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/viewer/application/viewer_session.dart';
import 'package:xjicloud_mobile/features/viewer/domain/model_source.dart';
import 'package:xjicloud_mobile/features/viewer/domain/model_source_port.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_engine_port.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_capabilities.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

class Sources implements ModelSourcePort {
  final pending = <Completer<Result<LocalModelRef>>>[];
  @override
  Future<Result<LocalModelRef>> resolve(
    ModelSource source,
    Cancellation cancellation, {
    void Function(int, int?)? onProgress,
  }) {
    final c = Completer<Result<LocalModelRef>>();
    pending.add(c);
    return c.future;
  }
}

class Engine implements ViewerEnginePort {
  final events = StreamController<ViewerEngineState>.broadcast(sync: true);
  @override
  int activeRequestId = 0;
  int serial = 0;
  bool supported = true, early = false;
  bool failOpen = false;
  Completer<Result<void>>? closing;
  @override
  Stream<ViewerEngineState> get states => events.stream;
  @override
  Future<Result<ViewerCapabilities>> initialize() async =>
      Success(ViewerCapabilities(supported: supported));
  @override
  Future<Result<int>> open(LocalModelRef model) async {
    if (failOpen) return const Failure(DomainError('viewer_engine'));
    final id = ++serial;
    if (early) ready(id);
    return Success(id);
  }

  void ready(int id) {
    activeRequestId = id;
    events.add(
      ViewerEngineState(ViewerPhase.ready, requestId: id, activeRequestId: id),
    );
  }

  @override
  Future<Result<void>> cancel() async => const Success(null);
  @override
  Future<Result<void>> close() async =>
      closing == null ? const Success(null) : closing!.future;
  @override
  Future<Result<void>> fullscreen(bool v) async => const Success(null);
  @override
  Future<Result<void>> visible(bool v) async => const Success(null);
  @override
  Future<Result<void>> camera(
    ViewerCamera c, [
    List<double> v = const [],
  ]) async => const Success(null);
}

void main() {
  late Sources sources;
  late Engine engine;
  late ViewerSession session;
  late List<String> released;
  LocalModelRef model(String name) => LocalModelRef(
    handle: name,
    fileName: name,
    releaseResource: () async {
      released.add(name);
    },
  );
  Future<void> opened(String name) async {
    final f = session.open(const LocalModelSource());
    await Future<void>.delayed(Duration.zero);
    sources.pending.last.complete(Success(model(name)));
    await f;
    engine.ready(engine.serial);
  }

  setUp(() async {
    sources = Sources();
    engine = Engine();
    released = [];
    session = ViewerSession(sources, engine, changed: () {});
    await session.initialize();
  });
  test('failed B preserves active A and its lease', () async {
    await opened('A');
    final f = session.open(const LocalModelSource());
    await Future<void>.delayed(Duration.zero);
    sources.pending.last.complete(const Failure(DomainError('network')));
    await f;
    expect(session.active?.fileName, 'A');
    expect(released, isEmpty);
  });
  test(
    'failed initial open then cancel stays empty and releases once',
    () async {
      engine.failOpen = true;
      final opening = session.open(const LocalModelSource());
      await Future<void>.delayed(Duration.zero);
      sources.pending.last.complete(Success(model('B')));
      await opening;
      expect(session.phase, ViewerPhase.failed);
      expect(session.active, isNull);
      expect(released, isEmpty);

      await session.cancel();
      expect(session.phase, ViewerPhase.empty);
      expect(session.active, isNull);
      expect(session.error, isNull);
      expect(released, ['B']);
      await session.cancel();
      await session.close();
      expect(released, ['B']);
    },
  );
  test('late B download cannot replace C and releases its lease', () async {
    await opened('A');
    final b = session.open(const LocalModelSource());
    await Future<void>.delayed(Duration.zero);
    final pb = sources.pending.last;
    final c = session.open(const LocalModelSource());
    await Future<void>.delayed(Duration.zero);
    sources.pending.last.complete(Success(model('C')));
    await c;
    engine.ready(engine.serial);
    pb.complete(Success(model('B')));
    await b;
    expect(session.active?.fileName, 'C');
    expect(released, containsAll(['A', 'B']));
  });
  test(
    'close invalidates late resolution and waits for native acknowledgement',
    () async {
      await opened('A');
      final f = session.open(const LocalModelSource());
      await Future<void>.delayed(Duration.zero);
      final pending = sources.pending.last;
      engine.closing = Completer();
      final closing = session.close();
      await Future<void>.delayed(Duration.zero);
      expect(released, isEmpty);
      pending.complete(Success(model('B')));
      await f;
      expect(released, ['B']);
      engine.closing!.complete(const Success(null));
      await closing;
      expect(released, contains('A'));
      expect(session.active, isNull);
    },
  );
  test(
    'first frame before open response still commits matching candidate',
    () async {
      engine.early = true;
      await opened('A');
      expect(session.active?.fileName, 'A');
    },
  );
  test(
    'mismatched active request never commits or releases old scene',
    () async {
      await opened('A');
      final f = session.open(const LocalModelSource());
      await Future<void>.delayed(Duration.zero);
      sources.pending.last.complete(Success(model('B')));
      await f;
      engine.events.add(
        const ViewerEngineState(
          ViewerPhase.ready,
          requestId: 2,
          activeRequestId: 1,
        ),
      );
      expect(session.active?.fileName, 'A');
      expect(released, isEmpty);
      engine.ready(2);
      await Future<void>.delayed(Duration.zero);
      expect(session.active?.fileName, 'B');
      expect(released, ['A']);
    },
  );
  test('late native B frame cannot commit after C request starts', () async {
    await opened('A');
    final b = session.open(const LocalModelSource());
    await Future<void>.delayed(Duration.zero);
    sources.pending.last.complete(Success(model('B')));
    await b;
    final c = session.open(const LocalModelSource());
    engine.events.add(
      const ViewerEngineState(
        ViewerPhase.ready,
        requestId: 2,
        activeRequestId: 2,
      ),
    );
    await Future<void>.delayed(Duration.zero);
    expect(session.active?.fileName, 'A');
    sources.pending.last.complete(Success(model('C')));
    await c;
    engine.ready(3);
    expect(session.active?.fileName, 'C');
  });
  test('native B won before cancellation: metadata and lease follow B when C fails', () async {
    await opened('A');
    final b = session.open(const LocalModelSource());
    await Future<void>.delayed(Duration.zero);
    sources.pending.last.complete(Success(model('B')));
    await b;
    final c = session.open(const LocalModelSource());
    engine.ready(2);
    await Future<void>.delayed(Duration.zero);
    expect(session.active?.fileName, 'B');
    expect(released, ['A']);
    sources.pending.last.complete(const Failure(DomainError('network')));
    await c;
    expect(session.active?.fileName, 'B');
    expect(released, ['A']);
    await session.close();
    expect(released, ['A', 'B']);
  });
  test(
    'unsupported platform produces explicit state without resolving source',
    () async {
      engine.supported = false;
      await session.initialize();
      await session.open(const LocalModelSource());
      expect(session.phase, ViewerPhase.unsupported);
      expect(sources.pending, isEmpty);
    },
  );
}
