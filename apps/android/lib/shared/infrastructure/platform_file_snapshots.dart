import 'package:flutter/services.dart';

/// A single native file seam shared by image and model adapters. No file bytes cross it.
final class PlatformFileSnapshots {
  PlatformFileSnapshots({MethodChannel? channel})
    : channel = channel ?? const MethodChannel('xjicloud/platform_files');
  final MethodChannel channel;
  Future<String> directory(String kind) async {
    final data = await channel.invokeMapMethod<String, dynamic>('directories');
    final value = data?[kind];
    if (value is! String || value.isEmpty) {
      throw PlatformException(code: 'storage');
    }
    return value;
  }
}
