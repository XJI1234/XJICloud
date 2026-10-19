import 'dart:io';

import 'package:flutter/services.dart';

import '../../../shared/domain/cancellation.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/model_source.dart';
import '../domain/model_source_port.dart';

/// All cross-context ownership is supplied by the sole composition root.
final class ModelSourceAdapter implements ModelSourcePort {
  const ModelSourceAdapter({required this.acquire});
  final Future<Result<LocalModelRef>> Function(
    ModelSource,
    Cancellation,
    void Function(int, int?)?,
  )
  acquire;
  @override
  Future<Result<LocalModelRef>> resolve(
    ModelSource source,
    Cancellation cancellation, {
    void Function(int, int?)? onProgress,
  }) async {
    try {
      return await acquire(source, cancellation, onProgress);
    } on DomainError catch (e) {
      return Failure(e);
    } on FileSystemException {
      return const Failure(DomainError('storage'));
    } on PlatformException {
      return const Failure(DomainError('storage'));
    }
  }
}
