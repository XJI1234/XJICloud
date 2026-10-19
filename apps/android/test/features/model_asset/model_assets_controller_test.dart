import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_assets_controller.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/file_model_cache.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_cache_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'chunk_upload_test.dart' show Repo, Files, Journal, source, asset;

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

void main() {
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
