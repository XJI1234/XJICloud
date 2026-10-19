/// Architecture gate shared by real-source checks and adversarial fixtures.
/// Resolves relative and self-package imports/exports, including conditional URIs.
List<String> layerViolations(String sourcePath, String source) {
  final violations = <String>[];
  final domain = sourcePath.contains('/domain/');
  final application = sourcePath.contains('/application/');
  final presentation =
      sourcePath.startsWith('presentation/') ||
      sourcePath.contains('/presentation/');
  final app = sourcePath.startsWith('app/');
  final entry = RegExp(r'^main(?:_[a-zA-Z0-9_]+)?\.dart$').hasMatch(sourcePath);
  final owner = RegExp(r'^features/([^/]+)/').firstMatch(sourcePath)?.group(1);
  final directives = RegExp(r'''\b(?:import|export)\s+[^;]+;''');
  final uris = RegExp(r'''['"]([^'"]+)['"]''');
  for (final directive in directives.allMatches(source)) {
    for (final match in uris.allMatches(directive.group(0)!)) {
      final uri = match.group(1)!;
      final selfPackage = uri.startsWith('package:xjicloud_mobile/');
      final external = uri.startsWith('package:') && !selfPackage;
      final dart = uri.startsWith('dart:');
      final target = selfPackage
          ? uri.substring('package:xjicloud_mobile/'.length)
          : Uri.parse(sourcePath).resolve(uri).path;
      final inward =
          target.contains('/domain/') ||
          (application && target.contains('/application/'));
      final infra = target.contains('/infrastructure/');
      final targetOwner = RegExp(r'^features/([^/]+)/')
          .firstMatch(target)
          ?.group(1);
      final forbiddenDart = [
        'dart:io',
        'dart:ui',
        'dart:ffi',
        'dart:html',
        'dart:js',
        'dart:js_interop',
      ].contains(uri);
      String? reason;
      if ((domain || application) &&
          (external || forbiddenDart || (!dart && !inward))) {
        reason = 'inner layer depends on an outer layer';
      } else if (presentation &&
          (infra ||
              forbiddenDart ||
              uri.startsWith('package:dio/') ||
              uri.startsWith('package:http/') ||
              uri.startsWith('package:native3dgs_flutter/') ||
              uri == 'package:flutter/services.dart' ||
              RegExp(r'(?:^|/)\w*_dto\.dart$').hasMatch(target))) {
        reason = 'UI bypasses a controller or platform boundary';
      } else if (owner != null &&
          infra &&
          targetOwner != null &&
          owner != targetOwner) {
        reason = 'cross BC infrastructure dependency';
      } else if ((app || entry) &&
          sourcePath != 'app/create_container.dart' &&
          infra) {
        reason = 'infrastructure assembly outside create_container.dart';
      }
      if (reason != null) violations.add('$sourcePath -> $uri: $reason');
    }
  }
  return violations;
}
