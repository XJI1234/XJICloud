import 'dart:io';

import 'package:flutter/services.dart';

import '../../../shared/infrastructure/platform_file_snapshots.dart';
import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/selected_image.dart';

final class AndroidImageSourceAdapter implements ImageSourcePort {
  AndroidImageSourceAdapter(this.files);
  final PlatformFileSnapshots files;
  static final _id = RegExp(r'^[a-f0-9-]{36}$');
  Future<String> _path(SelectedImage source) async {
    if (!_id.hasMatch(source.sourceId)) {
      throw const DomainError('source_changed');
    }
    return '${await files.directory('sources')}/${source.sourceId}';
  }

  @override
  Future<Result<List<SelectedImage>?>> choose(
    bool directory,
    Cancellation cancellation,
  ) async {
    final detach = cancellation.listen(
      () => files.channel
          .invokeMethod<void>('cancelSelection')
          .catchError((Object _) {}),
    );
    try {
      if (cancellation.isCancelled) {
        return const Failure(DomainError('cancelled'));
      }
      final data = await files.channel.invokeListMethod<dynamic>(
        'chooseImages',
        {'directory': directory},
      );
      if (data == null) return const Success(null);
      if (data.isEmpty || data.length > 9999) {
        throw const DomainError('dataset_validation');
      }
      final selected = <SelectedImage>[];
      for (final value in data) {
        if (value is! Map ||
            value['id'] is! String ||
            !_id.hasMatch(value['id']) ||
            value['fileName'] is! String ||
            value['relativeName'] is! String ||
            value['contentType'] is! String ||
            value['sizeBytes'] is! int ||
            value['fingerprint'] is! String) {
          throw const DomainError('protocol');
        }
        selected.add(
          SelectedImage(
            sourceId: value['id'],
            name: value['fileName'],
            relativeName: value['relativeName'],
            contentType: value['contentType'],
            sizeBytes: value['sizeBytes'],
            fingerprint: value['fingerprint'],
          ),
        );
      }
      if (cancellation.isCancelled) {
        for (final source in selected) {
          await release(source);
        }
        return const Failure(DomainError('cancelled'));
      }
      return Success(List.unmodifiable(selected));
    } on PlatformException catch (e) {
      return Failure(
        DomainError(
          e.code == 'dataset_validation' || e.code == 'source_busy'
              ? e.code
              : 'storage',
        ),
      );
    } on DomainError catch (e) {
      return Failure(e);
    } finally {
      detach();
    }
  }

  @override
  Future<Result<void>> verify(SelectedImage source) async {
    if (!_id.hasMatch(source.sourceId)) {
      return const Failure(DomainError('source_changed'));
    }
    try {
      final data = await files.channel.invokeMapMethod<String, dynamic>(
        'fingerprint',
        {'id': source.sourceId},
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
  Stream<List<int>> read(SelectedImage source) async* {
    yield* File(await _path(source)).openRead();
  }

  @override
  Future<Result<void>> release(SelectedImage source) async {
    try {
      final file = File(await _path(source));
      if (await file.exists()) await file.delete();
      return const Success(null);
    } on FileSystemException {
      return const Failure(DomainError('storage'));
    } on PlatformException {
      return const Failure(DomainError('storage'));
    } on DomainError catch (e) {
      return Failure(e);
    }
  }
}
