import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_assets_controller.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/file_model_cache.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/http_model_asset_repository.dart';
import 'package:xjicloud_mobile/shared/infrastructure/api_client.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_cache_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'chunk_upload_test.dart' show Repo, Files, Journal, source, asset;
import 'model_repository_test.dart'
    as transport_fixture
    show BinaryTransport, id;

import 'dart:io';

class DelayedRepo extends Repo {
  final gate = Completer<Result<List<ModelAsset>>>();
  @override
  Future<Result<List<ModelAsset>>> list(String projectId, int generation) =>
      gate.future;
}

class PickerFiles extends Files {
  final selected = Completer<Result<LocalModelRef?>>();
  Cancellation? token;
  @override
  Future<Result<LocalModelRef?>> choose(Cancellation cancellation) {
    token = cancellation;
    return selected.future;
  }
}

class UnexpectedDownloadRepo extends Repo {
  bool broken = true;
  @override
  Future<Result<void>> downloadToFile(
    String id,
    int generation,
    Future<void> Function(Stream<List<int>>) consume,
    Cancellation cancellation,
  ) async {
    if (broken) throw StateError('unexpected adapter failure');
    await consume(Stream.value(List.filled(asset.sizeBytes, 1)));
    return const Success(null);
  }
}

void main() {
  test(
    'unexpected download rejection always clears busy and task bookkeeping',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      addTearDown(sessions.dispose);
      final root = await Directory.systemTemp.createTemp(
        'xji-controller-rejection-',
      );
      addTearDown(() => root.delete(recursive: true));
      final repo = UnexpectedDownloadRepo();
      final cache = FileModelCache(() async => root.path);
      final c = ModelAssetsController(
        repo,
        Files(),
        Journal(),
        cache,
        sessions,
      );
      addTearDown(c.dispose);
      c.synchronize();
      await expectLater(c.download(asset), throwsStateError);
      expect(c.downloading(asset.id), isFalse);
      expect(c.downloadProgress, isEmpty);
      expect((await cache.size(c.userId!) as Success<int>).value, 0);
      repo.broken = false;
      await c.download(asset);
      expect(c.downloading(asset.id), isFalse);
      expect(c.cached(asset), isTrue);
      expect(await c.stopProjectTransfers('p'), isA<Success<void>>());
    },
  );
  test('actual response stream reset clears controller bookkeeping and allows another download', () async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    addTearDown(sessions.dispose);
    final root = await Directory.systemTemp.createTemp(
      'xji-controller-stream-',
    );
    addTearDown(() => root.delete(recursive: true));
    final transport = transport_fixture.BinaryTransport()
      ..binary = true
      ..bodyError = const SocketException('mid-body reset');
    final api = ApiClient(
      origin: 'https://api.example.test',
      sessions: sessions,
      dio: Dio()..httpClientAdapter = transport,
    );
    addTearDown(api.close);
    final cache = FileModelCache(() async => root.path);
    final c = ModelAssetsController(
      HttpModelAssetRepository(api),
      Files(),
      Journal(),
      cache,
      sessions,
    );
    addTearDown(c.dispose);
    final model = ModelAsset(
      id: transport_fixture.id,
      projectId: 'p',
      fileName: 'a.ply',
      format: 'PLY',
      sizeBytes: 4,
      version: 1,
      createdAt: DateTime.utc(2026),
      updatedAt: DateTime.utc(2026),
    );
    c.synchronize();
    await c.download(model);
    expect(c.error?.code, 'network');
    expect(c.downloading(model.id), isFalse);
    expect(c.downloadProgress.containsKey(model.id), isFalse);
    expect(await root.list().length, 0);
    transport.bodyError = null;
    await c.download(model);
    expect(c.cached(model), isTrue);
    expect(c.downloading(model.id), isFalse);
    expect(c.downloadProgress.containsKey(model.id), isFalse);
    expect(await c.stopProjectTransfers('p'), isA<Success<void>>());
  });
  test(
    'foreground refresh removes cache badge after private cache eviction',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      addTearDown(sessions.dispose);
      final root = await Directory.systemTemp.createTemp('xji-cache-badge-');
      addTearDown(() => root.delete(recursive: true));
      final cache = FileModelCache(() => Future.value(root.path));
      final c = ModelAssetsController(
        Repo(),
        Files(),
        Journal(),
        cache,
        sessions,
      );
      addTearDown(c.dispose);
      c.synchronize();
      final saved = await cache.store(
        c.userId!,
        asset,
        (consume) async {
          await consume(Stream.value(List.filled(asset.sizeBytes, 1)));
          return const Success(null);
        },
        Cancellation(),
        () => true,
      );
      (saved as Success<CacheLease>).value.release();
      await c.refresh('p');
      expect(c.cached(asset), isTrue);
      await cache.clear(c.userId!);
      await c.refreshLoaded();
      expect(c.cached(asset), isFalse);
    },
  );
  test('late owned list cannot cross session generation; synchronously clears upload on logout', () async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    final root = await Directory.systemTemp.createTemp('xji-controller-');
    addTearDown(() => root.delete(recursive: true));
    addTearDown(sessions.dispose);
    final repo = DelayedRepo();
    final controller = ModelAssetsController(
      repo,
      Files(),
      Journal(),
      FileModelCache(() => Future.value(root.path)),
      sessions,
    );
    addTearDown(controller.dispose);
    controller.synchronize();
    final pending = controller.refresh('p');
    await sessions.invalidate();
    controller.synchronize();
    repo.gate.complete(const Success([]));
    await pending;
    expect(controller.userId, isNull);
    expect(controller.modelsFor('p'), isEmpty);
    expect(controller.checkpoint, isNull);
    expect(controller.uploadBusy, isFalse);
  });
  test(
    'upload target remains captured project even after viewing another project',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final root = await Directory.systemTemp.createTemp('xji-controller-');
      addTearDown(() => root.delete(recursive: true));
      addTearDown(sessions.dispose);
      final journal = Journal();
      final controller = ModelAssetsController(
        Repo(),
        Files(),
        journal,
        FileModelCache(() => Future.value(root.path)),
        sessions,
      );
      addTearDown(controller.dispose);
      controller.synchronize();
      await Future<void>.delayed(Duration.zero);
      await controller.chooseAndUpload('p');
      expect(controller.checkpoint!.projectId, 'p');
      await controller.refresh('another');
      expect(controller.checkpoint!.projectId, 'p');
    },
  );
  test('SAF Activity pause preserves selection and waits for foreground before creating upload', () async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    addTearDown(sessions.dispose);
    final root = await Directory.systemTemp.createTemp('xji-saf-');
    addTearDown(() => root.delete(recursive: true));
    final files = PickerFiles(), repo = Repo();
    final controller = ModelAssetsController(
      repo,
      files,
      Journal(),
      FileModelCache(() => Future.value(root.path)),
      sessions,
    );
    addTearDown(controller.dispose);
    controller.synchronize();
    await Future<void>.delayed(Duration.zero);
    final uploading = controller.chooseAndUpload('p');
    controller.background();
    expect(files.token!.isCancelled, isFalse);
    await controller.stopProjectTransfers('another');
    expect(files.token!.isCancelled, isFalse);
    files.selected.complete(const Success(source));
    await Future<void>.delayed(Duration.zero);
    expect(repo.gets, 0);
    expect(controller.uploadBusy, isTrue);
    controller.foreground();
    await uploading;
    expect(controller.checkpoint!.projectId, 'p');
    expect(repo.completes, 1);
  });
  test('late list started before confirmed deletion cannot resurrect a removed model', () async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    addTearDown(sessions.dispose);
    final root = await Directory.systemTemp.createTemp('xji-stale-list-');
    addTearDown(() => root.delete(recursive: true));
    final repo = DelayedRepo();
    final c = ModelAssetsController(
      repo,
      Files(),
      Journal(),
      FileModelCache(() => Future.value(root.path)),
      sessions,
    );
    addTearDown(c.dispose);
    c.synchronize();
    final pending = c.refresh('p');
    await c.delete(asset);
    repo.gate.complete(Success([asset]));
    await pending;
    expect(c.modelsFor('p'), isEmpty);
  });
}
