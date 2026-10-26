import 'package:flutter/services.dart';

import '../domain/domain_error.dart';

/// A single native file seam shared by image and model adapters. No file bytes cross it.
final class PlatformFileSnapshots {
  PlatformFileSnapshots({MethodChannel? channel})
    : channel = channel ?? const MethodChannel('xjicloud/platform_files');
  final MethodChannel channel;
  Future<void> Function()? initialize;
  Future<void>? _ready;

  /// One startup barrier shared by pickers and cold-resume reads.
  Future<void> ready() => _ready ??= (() async {
    try {
      await initialize?.call();
    } catch (_) {
      throw const DomainError('storage');
    }
  })();
  Future<String> directory(String kind) async {
    final data = await channel.invokeMapMethod<String, dynamic>('directories');
    final value = data?[kind];
    if (value is! String || value.isEmpty) {
      throw PlatformException(code: 'storage');
    }
    return value;
  }
}
