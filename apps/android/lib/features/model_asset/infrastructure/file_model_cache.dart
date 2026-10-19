import 'dart:convert';
import 'dart:io';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/model_asset.dart';
import '../domain/model_revision.dart';
import '../domain/local_file_port.dart';
import '../domain/model_cache_port.dart';

/// All mutations share one queue, including streaming writes and lease cleanup.
/// Paths are derived exclusively from encoded identities, never server filenames.
final class FileModelCache implements ModelCachePort {
  FileModelCache(this.rootPath, {this.quotaBytes = 4 * 1024 * 1024 * 1024});
  final Future<String> Function() rootPath;
  final int quotaBytes;
  final _leases = <String, int>{};
  final _invalid = <String>{};
  Future<void> _tail = Future.value();
  bool _initialized = false;
  static String _encode(String value) =>
      base64Url.encode(utf8.encode(value)).replaceAll('=', '');
  Future<Result<T>> _serial<T>(Future<Result<T>> Function() operation) {
    final result = _tail.then((_) async {
      try {
        return await operation();
      } on FileSystemException {
        return Failure<T>(const DomainError('storage'));
      } on FormatException {
        return Failure<T>(const DomainError('storage'));
      } on DomainError catch (error) {
        return Failure<T>(error);
      }
    });
    _tail = result.then<void>((_) {});
    return result;
  }

  Future<Directory> _root() async {
    final root = await Directory(await rootPath()).create(recursive: true);
    if (!_initialized) {
      await for (final file in root.list()) {
        if (file is File &&
            (file.path.endsWith('.part') || file.path.endsWith('.tmp'))) {
          await file.delete();
        }
        if (file is File &&
            file.path.endsWith('.bin') &&
            !await File('${file.path.substring(0, file.path.length - 4)}.json')
                .exists()) {
          await file.delete();
        }
      }
      _initialized = true;
    }
    return root;
  }

  Future<List<_Entry>> _entries(Directory root) async {
    final entries = <_Entry>[];
    await for (final file in root.list()) {
      if (file is! File || !file.path.endsWith('.json')) continue;
      if (await file.length() > 16384) throw const FormatException('cache');
      final data = jsonDecode(await file.readAsString());
      if (data is! Map<String, dynamic> ||
          data['userId'] is! String ||
          data['modelId'] is! String ||
          data['projectId'] is! String ||
          data['key'] is! String ||
          data['fileName'] is! String ||
          data['sizeBytes'] is! int ||
          data['sizeBytes'] < 1) {
        throw const FormatException('cache');
      }
      final stem = _encode(data['key'] as String);
      if (file.path != '${root.path}/$stem.json' &&
          file.absolute.path.replaceAll('\\', '/') !=
              '${root.absolute.path.replaceAll('\\', '/')}/$stem.json') {
        throw const FormatException('cache identity');
      }
      entries.add(_Entry(stem, data, File('${root.path}/$stem.bin'), file));
    }
    return entries;
  }

  CacheLease _lease(_Entry entry) {
    _leases.update(entry.stem, (value) => value + 1, ifAbsent: () => 1);
    return CacheLease(
      LocalModelRef(
        id: 'cache:${entry.stem}',
        fileName: entry.data['fileName'] as String,
        sizeBytes: entry.data['sizeBytes'] as int,
        fingerprint: entry.data['key'] as String,
      ),
      () {
        final count = (_leases[entry.stem] ?? 1) - 1;
        if (count > 0) {
          _leases[entry.stem] = count;
          return;
        }
        _leases.remove(entry.stem);
        if (_invalid.contains(entry.stem)) {
          _serial<void>(() async {
            await _remove(entry);
            return const Success(null);
          });
        }
      },
    );
  }

  Future<void> _remove(_Entry entry) async {
    if (await entry.meta.exists()) await entry.meta.delete();
    if (await entry.file.exists()) await entry.file.delete();
    _invalid.remove(entry.stem);
  }

  bool _leased(_Entry e) => (_leases[e.stem] ?? 0) > 0;
  @override
  Future<Result<List<ModelCacheEntry>>> entries(String userId) =>
      _serial(() async {
        final items = <ModelCacheEntry>[];
        for (final e in await _entries(await _root())) {
          if (e.data['userId'] != userId || !await e.file.exists()) continue;
          items.add(
            ModelCacheEntry(
              key: e.data['key'] as String,
              modelId: e.data['modelId'] as String,
              projectId: e.data['projectId'] as String,
              fileName: e.data['fileName'] as String,
              sizeBytes: await e.file.length(),
              inUse: _leased(e),
            ),
          );
        }
        return Success(List.unmodifiable(items));
      });
  @override
  Future<Result<int>> evict(String userId, String key) => _serial(() async {
    final entry = (await _entries(await _root()))
        .where((e) => e.data['userId'] == userId && e.data['key'] == key)
        .firstOrNull;
    if (entry == null) return const Success(0);
    if (_leased(entry)) return const Failure(DomainError('cache_in_use'));
    final bytes = await entry.file.exists() ? await entry.file.length() : 0;
    await _remove(entry);
    return Success(bytes);
  });
  @override
  Future<Result<CacheLease?>> acquire(String userId, ModelAsset asset) =>
      _serial(() async {
        final key = ModelRevision.of(userId, asset).key;
        final entries = await _entries(await _root());
        final entry = entries
            .where((e) => e.data['key'] == key && e.data['userId'] == userId)
            .firstOrNull;
        if (entry == null ||
            _invalid.contains(entry.stem) ||
            entry.data['invalid'] == true) {
          return const Success(null);
        }
        if (!await entry.file.exists() ||
            await entry.file.length() != asset.sizeBytes) {
          if (!_leased(entry)) await _remove(entry);
          return const Success(null);
        }
        await entry.meta.setLastModified(DateTime.now());
        return Success(_lease(entry));
      });
  @override
  Future<Result<CacheLease>> store(
    String userId,
    ModelAsset asset,
    Future<Result<void>> Function(Future<void> Function(Stream<List<int>>))
    download,
    Cancellation cancellation,
    bool Function() isCurrent, {
    void Function(int, int)? onProgress,
  }) => _serial(() async {
    final root = await _root();
    final entries = await _entries(root);
    final key = ModelRevision.of(userId, asset).key,
        stem = _encode(ModelRevision.of(userId, asset).key);
    bool active() => isCurrent() && !cancellation.isCancelled;
    if (!active()) return const Failure(DomainError('cancelled'));
    if (asset.sizeBytes < 1 || asset.sizeBytes > quotaBytes) {
      return const Failure(DomainError('cache_full'));
    }
    final existing = entries.where((e) => e.stem == stem).firstOrNull;
    if (existing != null &&
        !_invalid.contains(stem) &&
        existing.data['invalid'] != true &&
        await existing.file.exists() &&
        await existing.file.length() == asset.sizeBytes) {
      return Success(_lease(existing));
    }
    if (existing != null && _leased(existing)) {
      return const Failure(DomainError('cache_in_use'));
    }
    if (existing != null) {
      await _remove(existing);
      entries.remove(existing);
    }
    var used = 0;
    for (final e in entries) {
      if (await e.file.exists()) used += await e.file.length();
    }
    final dated = <(_Entry, DateTime)>[];
    for (final e in entries) {
      dated.add((e, await e.meta.lastModified()));
    }
    dated.sort((a, b) => a.$2.compareTo(b.$2));
    for (final item in dated) {
      if (used + asset.sizeBytes <= quotaBytes) break;
      final e = item.$1;
      if (_leased(e)) continue;
      final length = await e.file.exists() ? await e.file.length() : 0;
      await _remove(e);
      used -= length;
    }
    if (used + asset.sizeBytes > quotaBytes) {
      return const Failure(DomainError('cache_full'));
    }
    final part = File('${root.path}/$stem.part'),
        temporary = File('${root.path}/$stem.tmp');
    final entry = _Entry(
      stem,
      {
        'key': key,
        'userId': userId,
        'modelId': asset.id,
        'projectId': asset.projectId,
        'fileName': asset.fileName,
        'sizeBytes': asset.sizeBytes,
      },
      File('${root.path}/$stem.bin'),
      File('${root.path}/$stem.json'),
    );
    var committed = false, received = 0, consumed = false;
    try {
      final result = await download((stream) async {
        if (consumed) throw const DomainError('protocol');
        consumed = true;
        final output = await part.open(mode: FileMode.write);
        try {
          await for (final bytes in stream) {
            if (!active()) throw const DomainError('cancelled');
            received += bytes.length;
            if (received > asset.sizeBytes) throw const DomainError('protocol');
            await output.writeFrom(bytes);
            onProgress?.call(received, asset.sizeBytes);
          }
          await output.flush();
        } finally {
          await output.close();
        }
      });
      if (result case Failure<void>(:final error)) return Failure(error);
      if (!active()) return const Failure(DomainError('cancelled'));
      if (!consumed || received != asset.sizeBytes) {
        return const Failure(DomainError('protocol'));
      }
      await temporary.writeAsString(jsonEncode(entry.data), flush: true);
      if (!active()) return const Failure(DomainError('cancelled'));
      await part.rename(entry.file.path);
      if (!active()) return const Failure(DomainError('cancelled'));
      await temporary.rename(entry.meta.path);
      if (!active()) return const Failure(DomainError('cancelled'));
      committed = true;
      _invalid.remove(stem);
      return Success(_lease(entry));
    } finally {
      if (await part.exists()) await part.delete();
      if (await temporary.exists()) await temporary.delete();
      if (!committed) await _remove(entry);
    }
  });
  @override
  Future<Result<int>> clear(String userId) => _serial(() async {
    var freed = 0;
    for (final e in await _entries(await _root())) {
      if (e.data['userId'] != userId || _leased(e)) continue;
      if (await e.file.exists()) freed += await e.file.length();
      await _remove(e);
    }
    return Success(freed);
  });
  @override
  Future<Result<int>> size(String userId) => _serial(() async {
    var bytes = 0;
    for (final e in await _entries(await _root())) {
      if (e.data['userId'] == userId && await e.file.exists()) {
        bytes += await e.file.length();
      }
    }
    return Success(bytes);
  });
  Future<void> _invalidate(_Entry e) async {
    _invalid.add(e.stem);
    if (!_leased(e)) {
      await _remove(e);
      return;
    }
    e.data['invalid'] = true;
    final temporary = File('${e.meta.path}.tmp');
    await temporary.writeAsString(jsonEncode(e.data), flush: true);
    await temporary.rename(e.meta.path);
  }

  @override
  Future<Result<void>> invalidate(String userId, String modelId) =>
      _serial(() async {
        for (final e in await _entries(await _root())) {
          if (e.data['userId'] == userId && e.data['modelId'] == modelId) {
            await _invalidate(e);
          }
        }
        return const Success(null);
      });
  @override
  Future<Result<void>> retain(
    String userId,
    String projectId,
    List<ModelAsset> assets,
  ) => _serial(() async {
    final keys = assets.map((a) => ModelRevision.of(userId, a).key).toSet();
    for (final e in await _entries(await _root())) {
      if (e.data['userId'] == userId &&
          e.data['projectId'] == projectId &&
          !keys.contains(e.data['key'])) {
        await _invalidate(e);
      }
    }
    return const Success(null);
  });

  /// Viewer infrastructure can resolve only an acquired lease, never a URL.
  Future<String> resolve(CacheLease lease) async {
    final id = lease.file.id;
    if (lease.isReleased ||
        !id.startsWith('cache:') ||
        !RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(id.substring(6)) ||
        (_leases[id.substring(6)] ?? 0) < 1) {
      throw const DomainError('cache_in_use');
    }
    return '${(await _root()).path}/${id.substring(6)}.bin';
  }
}

final class _Entry {
  _Entry(this.stem, this.data, this.file, this.meta);
  final String stem;
  final Map<String, dynamic> data;
  final File file, meta;
}
