import 'dart:io';

import 'package:flutter/services.dart';

import '../../../shared/infrastructure/platform_file_snapshots.dart';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/local_file_port.dart';

/// Narrow platform seam. SAF URIs never escape this adapter or cross into UI.
final class AndroidLocalFileAdapter implements LocalFilePort {
  AndroidLocalFileAdapter({
    MethodChannel? channel,
    PlatformFileSnapshots? snapshots,
  }) : _snapshots = snapshots ?? PlatformFileSnapshots(channel: channel);
  final PlatformFileSnapshots _snapshots;
  MethodChannel get _channel => _snapshots.channel;
  static final _id = RegExp(r'^[a-f0-9-]{36}$');
  Future<String> _directory(String kind) => _snapshots.directory(kind);

  Future<String> cacheDirectory() => _directory('cache');
  Future<String> _sourcePath(LocalModelRef source) async {
    if (!_id.hasMatch(source.id)) throw const DomainError('source_changed');
    return '${await _directory('sources')}/${source.id}';
  }

  @override
  Future<Result<LocalModelRef?>> choose(Cancellation cancellation) async {
    bool cancelFailed = false;
    final detach = cancellation.listen(() {
      _channel.invokeMethod<void>('cancelSelection').catchError((Object _) {
        cancelFailed = true;
      });
    });
    try {
      if (cancellation.isCancelled) {
        return const Failure(DomainError('cancelled'));
      }
      final data = await _channel.invokeMapMethod<String, dynamic>(
        'chooseModel',
      );
      if (data == null) {
        return cancelFailed
            ? const Failure(DomainError('cleanup'))
            : const Success(null);
      }
      if (data['id'] is! String ||
          !_id.hasMatch(data['id']) ||
          data['fileName'] is! String ||
          data['sizeBytes'] is! int ||
          data['fingerprint'] is! String) {
        throw const DomainError('protocol');
      }
      final source = LocalModelRef(
        id: data['id'],
        fileName: data['fileName'],
        sizeBytes: data['sizeBytes'],
        fingerprint: data['fingerprint'],
      );
      if (cancellation.isCancelled) {
        final released = await release(source);
        if (released is Failure<void> || cancelFailed) {
          return const Failure(DomainError('cleanup'));
        }
        return const Failure(DomainError('cancelled'));
      }
      return Success(source);
    } on PlatformException catch (error) {
      return Failure(
        DomainError(
          ['model_validation', 'source_busy'].contains(error.code)
              ? error.code
              : 'storage',
        ),
      );
    } on DomainError catch (error) {
      return Failure(error);
    } finally {
      detach();
    }
  }

  @override
  Future<Result<void>> verify(LocalModelRef source) async {
    if (!_id.hasMatch(source.id)) {
      return const Failure(DomainError('source_changed'));
    }
    try {
      final data = await _channel.invokeMapMethod<String, dynamic>(
        'fingerprint',
        {'id': source.id},
      );
      return data?['sizeBytes'] == source.sizeBytes &&
              data?['fingerprint'] == source.fingerprint
          ? const Success(null)
          : const Failure(DomainError('source_changed'));
    } on PlatformException {
      return const Failure(DomainError('source_changed'));
    }
  }

  @override
  Stream<List<int>> read(
    LocalModelRef source,
    int start,
    int endExclusive,
  ) async* {
    if (start < 0 || endExclusive > source.sizeBytes || endExclusive <= start) {
      throw const DomainError('source_changed');
    }
    yield* File(await _sourcePath(source)).openRead(start, endExclusive);
  }

  /// Supplied by the composition root to future Viewer infrastructure only.
  Future<String> resolveSource(LocalModelRef source) async {
    final verified = await verify(source);
    if (verified case Failure<void>(:final error)) throw error;
    return _sourcePath(source);
  }

  @override
  Future<Result<void>> release(LocalModelRef source) async {
    try {
      final file = File(await _sourcePath(source));
      if (await file.exists()) await file.delete();
      return const Success(null);
    } on FileSystemException {
      return const Failure(DomainError('storage'));
    } on PlatformException {
      return const Failure(DomainError('storage'));
    } on DomainError catch (error) {
      return Failure(error);
    }
  }
}
