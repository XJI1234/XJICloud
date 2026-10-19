import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xjicloud_mobile/features/viewer/domain/model_source.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_capabilities.dart';
import 'package:xjicloud_mobile/features/viewer/infrastructure/model_source_adapter.dart';
import 'package:xjicloud_mobile/features/viewer/infrastructure/native3dgs_engine_adapter.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_controller.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_page.dart';
import 'package:xjicloud_mobile/features/identity/domain/session_access.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/infrastructure/platform_file_snapshots.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';

/// Only renderer identity is isolated. No network, credential or SDK mock.
class RendererIdentity implements SessionAccess {
  @override
  int generation = 1;
  @override
  UserSession? session = UserSession(
    userId: 'renderer-test',
    username: 'renderer-test',
    displayName: 'Renderer test',
    token: 'not-a-network-token',
    expiresAt: DateTime.now().add(const Duration(hours: 1)),
  );
  @override
  Future<Result<void>> invalidate({int? expectedGeneration}) async {
    generation++;
    session = null;
    return const Success(null);
  }
}

Uint8List graphdecoFixture() {
  const properties = [
    'x',
    'y',
    'z',
    'nx',
    'ny',
    'nz',
    'f_dc_0',
    'f_dc_1',
    'f_dc_2',
    'opacity',
    'scale_0',
    'scale_1',
    'scale_2',
    'rot_0',
    'rot_1',
    'rot_2',
    'rot_3',
  ];
  final header = utf8.encode(
    'ply\nformat binary_little_endian 1.0\nelement vertex 4096\n${properties.map((p) => 'property float $p').join('\n')}\nend_header\n',
  );
  final bytes = Uint8List(header.length + 4096 * 17 * 4)..setAll(0, header);
  final data = ByteData.sublistView(bytes);
  var offset = header.length;
  for (var i = 0; i < 4096; i++) {
    final z = 1 - 2 * (i + .5) / 4096, phi = i * 2.399963229728653;
    final radius = sqrt(1 - z * z);
    final values = [
      radius * cos(phi),
      radius * sin(phi),
      z,
      0.0,
      0.0,
      0.0,
      1.0,
      .2,
      .1,
      3.0,
      -4.0,
      -4.0,
      -4.0,
      1.0,
      0.0,
      0.0,
      0.0,
    ];
    for (final value in values) {
      data.setFloat32(offset, value, Endian.little);
      offset += 4;
    }
  }
  return bytes;
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'real decoder transactional failure, visibility, camera and twenty close/reopens',
    (tester) async {
      final snapshots = PlatformFileSnapshots();
      await snapshots.ready();
      final directory = await snapshots.directory('cache');
      final good = File('$directory/viewer-lifecycle-valid.ply'),
          bad = File('$directory/viewer-lifecycle-invalid.ply');
      Native3dgsEngineAdapter? cleanupEngine;
      ViewerController? cleanupController;
      addTearDown(() async {
        try {
          await cleanupController?.close();
          await tester.pumpWidget(const SizedBox());
          final closed = await cleanupEngine?.close();
          if (closed case Failure<void>(:final error)) throw error;
          // A failed native acknowledgement preserves files that may be in use.
          if (await good.exists()) await good.delete();
          if (await bad.exists()) await bad.delete();
        } finally {
          cleanupController?.dispose();
          await cleanupEngine?.dispose();
        }
      });
      await good.writeAsBytes(graphdecoFixture(), flush: true);
      await bad.writeAsString('ply\nformat ascii 1.0\nend_header\ninvalid');
      final goodUri = await privateModelUri(good.path),
          badUri = await privateModelUri(bad.path);
      final engine = cleanupEngine = Native3dgsEngineAdapter();
      var selected = goodUri, releases = 0;
      final identity = RendererIdentity();
      final controller = cleanupController = ViewerController(
        ModelSourceAdapter(
          acquire: (source, c, progress) async => Success(
            LocalModelRef(
              handle: selected,
              fileName: selected == goodUri ? 'valid.ply' : 'invalid.ply',
              releaseResource: () async {
                releases++;
              },
            ),
          ),
        ),
        engine,
        identity,
      );
      await controller.setVisible(true);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ViewerPage(
            controller: controller,
            surfaceBuilder: engine.surface,
            onChooseCloud: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await controller.initialize();
      if (controller.session.phase == ViewerPhase.unsupported) {
        expect(controller.session.active, isNull);
        // This is an explicit capability test, not an arm64 rendering pass.
        return;
      }
      Future<void> until(bool Function() predicate) async {
        final deadline = DateTime.now().add(const Duration(seconds: 45));
        while (!predicate() && DateTime.now().isBefore(deadline)) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(
          predicate(),
          isTrue,
          reason:
              'Native lifecycle timed out: ${controller.session.phase} / ${controller.session.error?.code}',
        );
      }

      await controller.open(const LocalModelSource());
      await until(() => controller.session.phase == ViewerPhase.ready);
      final original = controller.session.active;
      selected = badUri;
      await controller.open(const LocalModelSource());
      await until(() => controller.session.phase == ViewerPhase.failed);
      expect(controller.session.active, same(original));
      await controller.cancel();
      await controller.camera(ViewerCamera.free);
      await controller.camera(ViewerCamera.fly, [0, 0, 1, .1]);
      await controller.camera(ViewerCamera.fixed);
      await controller.camera(ViewerCamera.dolly, [.5]);
      await controller.camera(ViewerCamera.flip, [1]);
      await controller.camera(ViewerCamera.reset);
      await controller.camera(ViewerCamera.fit);
      await controller.setVisible(false);
      await tester.pump(const Duration(milliseconds: 200));
      expect(controller.session.active, same(original));
      await controller.setVisible(true);
      selected = goodUri;
      for (var i = 0; i < 20; i++) {
        await controller.close();
        expect(controller.session.active, isNull);
        await controller.open(const LocalModelSource());
        await until(() => controller.session.phase == ViewerPhase.ready);
      }
      await identity.invalidate();
      controller.synchronize();
      await controller.close();
      expect(controller.session.active, isNull);
      expect(releases, 22); // first A, invalid B, twenty opened scenes
    },
  );
}
