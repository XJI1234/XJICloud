import 'dart:io';

import '../domain/result.dart';

/// Runs before any current-process source can be selected or leased. Only the
/// private source directory is swept; model cache and checkpoint files are not.
Future<void> reconcilePrivateSources(
  String directory,
  Iterable<Future<Result<Set<String>>> Function()> inventories,
) async {
  final retained = <String>{};
  for (final inventory in inventories) {
    switch (await inventory()) {
      case Success<Set<String>>(:final value):
        retained.addAll(value);
      case Failure<Set<String>>(:final error):
        throw error;
    }
  }
  final root = Directory(directory);
  if (!await root.exists()) return;
  final id = RegExp(r'^[a-f0-9-]{36}$');
  await for (final entity in root.list(followLinks: false)) {
    if (entity is! File) continue;
    final name = entity.uri.pathSegments.last;
    if (name.endsWith('.part') ||
        id.hasMatch(name) && !retained.contains(name)) {
      await entity.delete();
    }
  }
}
