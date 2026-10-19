import 'dart:convert';
import 'dart:io';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../domain/dataset_archive_policy.dart';
import '../domain/dataset_manifest.dart';
import '../domain/selected_image.dart';
import '../domain/upload_checkpoint_port.dart';

/// Immutable manifest is written once; small acknowledgment state is atomically replaced.
final class LocalUploadCheckpoint implements UploadCheckpointPort {
  LocalUploadCheckpoint(this.directory);
  final Future<String> Function() directory;

  /// Include every account and conservatively retain sources from a manifest
  /// whose state write was interrupted. Any unreadable inventory fails closed.
  Future<Result<Set<String>>> referencedSources() => _serial(() async {
    final root = Directory(await directory());
    final retained = <String>{};
    if (!await root.exists()) return retained;
    await for (final entity in root.list(followLinks: false)) {
      if (entity is! File) continue;
      if (entity.path.endsWith('.state.json')) {
        if (await entity.length() > 1024 * 1024) {
          throw const FormatException('size');
        }
        final sources = File(
          entity.path.replaceFirst(RegExp(r'\.state\.json$'), '.sources.json'),
        );
        if (!await sources.exists()) {
          throw const FormatException('missing sources');
        }
        final state = jsonDecode(await entity.readAsString());
        if (state is! Map<String, dynamic> || state['userId'] is! String) {
          throw const FormatException('state');
        }
      }
      if (!entity.path.endsWith('.sources.json')) continue;
      if (await entity.length() > 64 * 1024 * 1024) {
        throw const FormatException('size');
      }
      final items = jsonDecode(await entity.readAsString());
      if (items is! List || items.length > 9999) {
        throw const FormatException('sources');
      }
      for (final item in items) {
        if (item is! Map<String, dynamic> ||
            item['sourceId'] is! String ||
            !RegExp(r'^[a-f0-9-]{36}$').hasMatch(item['sourceId'])) {
          throw const FormatException('source');
        }
        retained.add(item['sourceId']);
      }
    }
    return retained;
  });
  Future<void> _tail = Future.value();
  Future<Result<T>> _serial<T>(Future<T> Function() action) {
    final result = _tail.then((_) async {
      try {
        return Success<T>(await action());
      } on FileSystemException {
        return Failure<T>(const DomainError('storage'));
      } on FormatException {
        return Failure<T>(const DomainError('protocol'));
      } on DomainError catch (e) {
        return Failure<T>(e);
      }
    });
    _tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  Future<String> _base(String user) async {
    final root = Directory(await directory());
    await root.create(recursive: true);
    return '${root.path}/${base64Url.encode(utf8.encode(user))}';
  }

  Future<void> _atomic(String path, Object data) async {
    final part = File('$path.part');
    await part.writeAsString(jsonEncode(data), flush: true);
    await part.rename(path);
  }

  @override
  Future<Result<void>> write(DatasetCheckpoint c) => _serial(() async {
    final base = await _base(c.userId);
    final manifest = File('$base.sources.json');
    if (c.phase == DatasetUploadPhase.creating || !await manifest.exists()) {
      await _atomic(manifest.path, [
        for (final f in c.manifest.files)
          {
            'sourceId': f.source.sourceId,
            'name': f.source.name,
            'relativeName': f.source.relativeName,
            'contentType': f.source.contentType,
            'sizeBytes': f.source.sizeBytes,
            'fingerprint': f.source.fingerprint,
          },
      ]);
    }
    await _atomic('$base.state.json', {
      'userId': c.userId,
      'projectId': c.projectId,
      'name': c.name,
      'jobId': c.jobId,
      'phase': c.phase.name,
      'confirmed': c.confirmed.toList(),
    });
  });
  @override
  Future<Result<DatasetCheckpoint?>> read(String user) => _serial(() async {
    final base = await _base(user),
        stateFile = File('$base.state.json'),
        manifestFile = File('$base.sources.json');
    if (!await stateFile.exists()) return null;
    if (await stateFile.length() > 1024 * 1024 ||
        await manifestFile.length() > 64 * 1024 * 1024) {
      throw const FormatException('checkpoint size');
    }
    final state = jsonDecode(await stateFile.readAsString()),
        items = jsonDecode(await manifestFile.readAsString());
    if (state is! Map<String, dynamic> ||
        state['userId'] != user ||
        state['projectId'] is! String ||
        state['name'] is! String ||
        state['jobId'] != null && state['jobId'] is! String ||
        state['confirmed'] is! List ||
        items is! List ||
        items.length > 9999) {
      throw const FormatException('checkpoint');
    }
    final images = <SelectedImage>[];
    for (final item in items) {
      if (item is! Map<String, dynamic> ||
          item['sourceId'] is! String ||
          item['name'] is! String ||
          item['relativeName'] is! String ||
          item['contentType'] is! String ||
          item['sizeBytes'] is! int ||
          item['fingerprint'] is! String) {
        throw const FormatException('source');
      }
      images.add(
        SelectedImage(
          sourceId: item['sourceId'],
          name: item['name'],
          relativeName: item['relativeName'],
          contentType: item['contentType'],
          sizeBytes: item['sizeBytes'],
          fingerprint: item['fingerprint'],
        ),
      );
    }
    final prepared = archiveImages(images);
    if (prepared is! Success<DatasetManifest>) {
      throw const FormatException('manifest');
    }
    final confirmed = <String>{},
        names = {
          ...prepared.value.files.map((f) => f.archivedName),
          'manifest.json',
        };
    for (final name in state['confirmed']) {
      if (name is! String || !names.contains(name)) {
        throw const FormatException('confirmed');
      }
      confirmed.add(name);
    }
    final phase = DatasetUploadPhase.values
        .where((p) => p.name == state['phase'])
        .firstOrNull;
    if (phase == null) throw const FormatException('phase');
    return DatasetCheckpoint(
      userId: user,
      projectId: state['projectId'],
      name: state['name'],
      jobId: state['jobId'],
      phase: phase,
      manifest: prepared.value,
      confirmed: confirmed,
    );
  });
  @override
  Future<Result<void>> remove(String user) => _serial(() async {
    final base = await _base(user);
    for (final suffix in ['state.json', 'sources.json']) {
      final f = File('$base.$suffix');
      if (await f.exists()) await f.delete();
    }
  });
}
