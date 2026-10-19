import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_cache_port.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/file_model_cache.dart';

ModelAsset model({String id = 'm', int revision = 1}) => ModelAsset(
  id: id,
  projectId: 'p',
  fileName: 'a.ply',
  format: 'PLY',
  sizeBytes: 4,
  version: revision,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026, 1, revision),
);
Future<Result<void>> download(
  Future<void> Function(Stream<List<int>>) consume,
) async {
  await consume(Stream.value([1, 2, 3, 4]));
  return const Success(null);
}

void main() {
  late Directory root;
  late FileModelCache cache;
  setUp(() async {
    root = await Directory.systemTemp.createTemp('xji-cache-');
    cache = FileModelCache(() => Future.value(root.path), quotaBytes: 8);
  });
  tearDown(() async {
    await root.delete(recursive: true);
  });
  test(
    'individual cache eviction is account scoped and refuses an active lease',
    () async {
      final owned = (await cache.store(
        'u',
        model(),
        download,
        Cancellation(),
        () => true,
      ) as Success<CacheLease>).value;
      final other = (await cache.store(
        'v',
        model(),
        download,
        Cancellation(),
        () => true,
      ) as Success<CacheLease>).value;
      other.release();
      final items =
          (await cache.entries('u') as Success<List<ModelCacheEntry>>).value;
      expect(items, hasLength(1));
      expect(items.single.fileName, 'a.ply');
      expect(items.single.inUse, isTrue);
      expect(
        (await cache.evict('u', items.single.key) as Failure<int>).error.code,
        'cache_in_use',
      );
      expect(
        (await cache.evict('v', items.single.key) as Success<int>).value,
        0,
      );
      owned.release();
      expect(
        (await cache.evict('u', items.single.key) as Success<int>).value,
        4,
      );
      expect((await cache.size('u') as Success<int>).value, 0);
      expect((await cache.size('v') as Success<int>).value, 4);
    },
  );
  test('atomic store cache hit account and revision isolation; leased clear preserves live file', () async {
    final lease = (await cache.store(
      'u',
      model(),
      download,
      Cancellation(),
      () => true,
    ) as Success<CacheLease>).value;
    expect(
      (await cache.acquire('v', model()) as Success<CacheLease?>).value,
      isNull,
    );
    expect(
      (await cache.acquire(
        'u',
        model(revision: 2),
      ) as Success<CacheLease?>).value,
      isNull,
    );
    expect((await cache.clear('u') as Success<int>).value, 0);
    lease.release();
    expect((await cache.clear('u') as Success<int>).value, 4);
    expect(
      (await cache.acquire('u', model()) as Success<CacheLease?>).value,
      isNull,
    );
  });
  test(
    'cancel after streamed bytes cannot publish .part or cache metadata',
    () async {
      final cancel = Cancellation();
      final result = await cache.store(
        'u',
        model(),
        (consume) async {
          await consume(Stream.value([1, 2, 3, 4]));
          cancel.cancel();
          return const Success(null);
        },
        cancel,
        () => true,
      );
      expect(result, isA<Failure<CacheLease>>());
      expect((await cache.size('u') as Success<int>).value, 0);
      expect(
        await root
            .list(recursive: true)
            .where((e) => e.path.endsWith('.part'))
            .length,
        0,
      );
    },
  );
  test(
    'late identity change after download refuses metadata and atomic commit',
    () async {
      var current = true;
      final result = await cache.store(
        'u',
        model(),
        (consume) async {
          await consume(Stream.value([1, 2, 3, 4]));
          current = false;
          return const Success(null);
        },
        Cancellation(),
        () => current,
      );
      expect(result, isA<Failure<CacheLease>>());
      expect((await cache.size('u') as Success<int>).value, 0);
    },
  );
  test('quota exhaustion and incomplete stream never commit', () async {
    final tiny = FileModelCache(() => Future.value(root.path), quotaBytes: 3);
    expect(
      await tiny.store('u', model(), download, Cancellation(), () => true),
      isA<Failure<CacheLease>>(),
    );
    expect(
      await cache.store(
        'u',
        model(),
        (consume) async {
          await consume(Stream.value([1]));
          return const Success(null);
        },
        Cancellation(),
        () => true,
      ),
      isA<Failure<CacheLease>>(),
    );
    expect((await cache.size('u') as Success<int>).value, 0);
  });
  test(
    'invalidating leased entry prevents new readers and deletes after release',
    () async {
      final lease = (await cache.store(
        'u',
        model(),
        download,
        Cancellation(),
        () => true,
      ) as Success<CacheLease>).value;
      await cache.invalidate('u', 'm');
      expect(
        (await cache.acquire('u', model()) as Success<CacheLease?>).value,
        isNull,
      );
      expect((await cache.size('u') as Success<int>).value, 4);
      lease.release();
      await Future<void>.delayed(const Duration(milliseconds: 30));
      expect((await cache.size('u') as Success<int>).value, 0);
    },
  );
  test(
    'disk failure during stream maps storage and removes partial output',
    () async {
      final result = await cache.store(
        'u',
        model(),
        (consume) async {
          await consume(
            Stream<List<int>>.error(const FileSystemException('disk full')),
          );
          return const Success(null);
        },
        Cancellation(),
        () => true,
      );
      expect((result as Failure).error.code, 'storage');
      expect((await cache.size('u') as Success<int>).value, 0);
      expect(
        await root
            .list(recursive: true)
            .where((e) => e.path.endsWith('.part'))
            .length,
        0,
      );
    },
  );
  test('clear only affects current user and leaves original sources untouched', () async {
    final ownedSource = File(
      '${root.parent.path}/${root.uri.pathSegments.where((p) => p.isNotEmpty).last}-source.ply',
    );
    await ownedSource.writeAsString('original');
    addTearDown(() => ownedSource.delete());
    final u = (await cache.store(
      'u',
      model(),
      download,
      Cancellation(),
      () => true,
    ) as Success<CacheLease>).value;
    u.release();
    final v = (await cache.store(
      'v',
      model(),
      download,
      Cancellation(),
      () => true,
    ) as Success<CacheLease>).value;
    v.release();
    expect((await cache.clear('u') as Success<int>).value, 4);
    expect((await cache.size('v') as Success<int>).value, 4);
    expect(await ownedSource.readAsString(), 'original');
  });
}
