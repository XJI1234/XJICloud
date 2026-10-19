import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'support/layer_rules.dart';

void main() {
  test(
    'rejects framework IO and adapters from Domain, including relative exports',
    () {
      for (final directive in [
        "import 'package:flutter/widgets.dart';",
        "import 'dart:io';",
        "export '../infrastructure/project_dto.dart';",
        "import 'package:dio/dio.dart';",
        "import 'dart:ffi';",
        "import 'package:xjicloud_mobile/app/app_container.dart';",
      ]) {
        expect(
          layerViolations('features/project/domain/project.dart', directive),
          isNotEmpty,
          reason: directive,
        );
      }
    },
  );

  test('Application depends only on Domain and Application', () {
    expect(
      layerViolations(
        'features/identity/application/login.dart',
        "import '../presentation/login_controller.dart';",
      ),
      isNotEmpty,
    );
    expect(
      layerViolations(
        'features/identity/application/login.dart',
        "import '../domain/auth_repository.dart';",
      ),
      isEmpty,
    );
  });

  test('UI cannot access HTTP DTO adapters platform channels or IO', () {
    for (final directive in [
      "import '../infrastructure/auth_repository.dart';",
      "import 'package:dio/dio.dart';",
      "import 'package:http/http.dart';",
      "import 'package:flutter/services.dart';",
      "import 'package:native3dgs_flutter/native3dgs_flutter.dart';",
      "import 'dart:io';",
    ]) {
      expect(
        layerViolations(
          'features/identity/presentation/login_page.dart',
          directive,
        ),
        isNotEmpty,
      );
    }
  });

  test('cross BC adapters and additional composition roots are rejected', () {
    const adapter =
        "import '../features/identity/infrastructure/auth_repository.dart';";
    expect(layerViolations('app/another_container.dart', adapter), isNotEmpty);
    expect(layerViolations('app/create_container.dart', adapter), isEmpty);
    expect(
      layerViolations(
        'features/project/infrastructure/project_repository.dart',
        "import '../../identity/infrastructure/auth_repository.dart';",
      ),
      isNotEmpty,
    );
  });

  test('conditional import cannot hide an IO dependency', () {
    expect(
      layerViolations(
        'shared/domain/model.dart',
        "import 'safe.dart' if (dart.library.io) 'dart:io';",
      ),
      isNotEmpty,
    );
  });

  test('current lib obeys layer boundaries; P0 probe is an explicit development exception', () {
    final violations = <String>[];
    for (final file in Directory(
      'lib',
    ).listSync(recursive: true).whereType<File>()) {
      if (!file.path.endsWith('.dart')) continue;
      final path = file.path.replaceAll('\\', '/').replaceFirst('lib/', '');
      // Reviewed P0 hosts its native probe directly. It is not a product page.
      if (path == 'app/engine_probe_page.dart') continue;
      violations.addAll(layerViolations(path, file.readAsStringSync()));
    }
    expect(violations, isEmpty);
  });
}
