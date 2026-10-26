import 'dart:convert';
import 'dart:io';
import 'dart:math';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/training_output_port.dart';

final class LeasedTrainingOutput {
  LeasedTrainingOutput(this.path, this._release);
  final String path;
  Future<void> Function()? _release;
  Future<void> release() async {
    final action = _release;
    _release = null;
    await action?.call();
  }
}

final class FileTrainingOutputs implements TrainingOutputPort {
  FileTrainingOutputs(this.directory, this.fetch, {this.beforeUse});
  final Future<void> Function()? beforeUse;
  Future<void>? _prepared;

  /// Output identities are process-local. Purge previous-process artifacts once,
  /// before publishing or resolving any current-process identity.
  Future<void> prepare() => _prepared ??= (() async {
    final root = Directory('${await directory()}/outputs');
    if (!await root.exists()) return;
    await for (final entity in root.list(recursive: true, followLinks: false)) {
      if (entity is File &&
          (entity.path.endsWith('.ply') || entity.path.endsWith('.part'))) {
        await entity.delete();
      }
    }
  })();
  final Future<String> Function() directory;
  final Future<Result<void>> Function(
    String,
    Cancellation,
    Future<void> Function(Stream<List<int>>, int?),
  )
  fetch;
  static const maxBytes = 2147483648;
  final _leases = <String, int>{};
  final _revoked = <String>{};
  Future<LeasedTrainingOutput> acquire(
    TrainingOutputRef output,
    String userId,
  ) async {
    if (_revoked.contains(output.id) ||
        output.userId != userId ||
        !identical(_issued[output.id], output)) {
      throw const DomainError('forbidden');
    }
    _leases.update(output.id, (n) => n + 1, ifAbsent: () => 1);
    Future<void> release() async {
      final count = (_leases[output.id] ?? 1) - 1;
      if (count > 0) {
        _leases[output.id] = count;
        return;
      }
      _leases.remove(output.id);
      if (_revoked.contains(output.id)) {
        final result = await remove(output);
        if (result case Failure<void>(:final error)) throw error;
      }
    }

    try {
      return LeasedTrainingOutput(await resolve(output, userId), release);
    } catch (_) {
      await release();
      rethrow;
    }
  }

  final _issued = <String, TrainingOutputRef>{};
  Future<File> _file(TrainingOutputRef ref) async {
    await beforeUse?.call();
    await prepare();
    return File(
      '${await directory()}/outputs/${base64Url.encode(utf8.encode(ref.userId))}/${ref.id}.ply',
    );
  }

  @override
  Future<Result<TrainingOutputRef>> download(
    String userId,
    String jobId,
    String url,
    Cancellation cancellation,
    void Function(int, int?) onProgress,
  ) async {
    final id = List.generate(
      24,
      (_) => Random.secure().nextInt(16).toRadixString(16),
    ).join();
    var output = TrainingOutputRef(
      id: id,
      userId: userId,
      jobId: jobId,
      sizeBytes: 0,
    );
    File? part;
    RandomAccessFile? handle;
    var count = 0;
    try {
      final destination = await _file(output);
      await destination.parent.create(recursive: true);
      part = File('${destination.path}.part');
      handle = await part.open(mode: FileMode.write);
      final result = await fetch(url, cancellation, (bytes, total) async {
        if (total != null && (total <= 0 || total > maxBytes)) {
          throw const DomainError('model_validation');
        }
        await for (final chunk in bytes) {
          if (cancellation.isCancelled) throw const DomainError('cancelled');
          count += chunk.length;
          if (count > maxBytes) throw const DomainError('model_validation');
          await handle!.writeFrom(chunk);
          onProgress(count, total);
        }
        if (count == 0 || total != null && count != total) {
          throw const DomainError('protocol');
        }
      });
      if (result case Failure<void>(:final error)) return Failure(error);
      if (cancellation.isCancelled) {
        return const Failure(DomainError('cancelled'));
      }
      await handle.flush();
      await handle.close();
      handle = null;
      await part.rename(destination.path);
      part = null;
      output = TrainingOutputRef(
        id: id,
        userId: userId,
        jobId: jobId,
        sizeBytes: count,
      );
      _issued[id] = output;
      return Success(output);
    } on DomainError catch (e) {
      return Failure(e);
    } on FileSystemException {
      return const Failure(DomainError('storage'));
    } on SocketException {
      return const Failure(DomainError('network'));
    } on HttpException {
      return const Failure(DomainError('network'));
    } finally {
      await handle?.close();
      if (part != null && await part.exists()) await part.delete();
    }
  }

  /// Task7 may resolve only an issued current-account identity inside infrastructure.
  Future<String> resolve(TrainingOutputRef output, String userId) async {
    if (_revoked.contains(output.id) ||
        output.userId != userId ||
        !identical(_issued[output.id], output)) {
      throw const DomainError('forbidden');
    }
    final file = await _file(output);
    if (!await file.exists() || await file.length() != output.sizeBytes) {
      throw const DomainError('source_changed');
    }
    return file.path;
  }

  @override
  Future<Result<void>> remove(TrainingOutputRef output) async {
    if (!identical(_issued[output.id], output)) {
      return const Failure(DomainError('forbidden'));
    }
    _revoked.add(output.id);
    if ((_leases[output.id] ?? 0) > 0) return const Success(null);
    try {
      final file = await _file(output);
      if (await file.exists()) await file.delete();
      _issued.remove(output.id);
      _revoked.remove(output.id);
      return const Success(null);
    } on FileSystemException {
      return const Failure(DomainError('storage'));
    }
  }
}
