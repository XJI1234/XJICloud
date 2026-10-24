import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:integration_test/integration_test.dart';
import 'package:native3dgs_flutter/native3dgs_view.dart';
import 'package:xjicloud_mobile/features/viewer/domain/model_source.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_capabilities.dart';
import 'package:xjicloud_mobile/features/viewer/infrastructure/model_source_adapter.dart';
import 'package:xjicloud_mobile/features/viewer/infrastructure/native3dgs_engine_adapter.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_controller.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_page.dart';
import 'package:xjicloud_mobile/presentation/shell/mobile_shell.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/infrastructure/platform_file_snapshots.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

import 'viewer_lifecycle_test.dart' show RendererIdentity, graphdecoFixture;

/// Real retained navigation + Android hybrid Surface. Identity/content for the
/// other tabs are isolated; this is not a backend or authentication E2E test.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('native canvas restores after projects and account tab visits', (
    tester,
  ) async {
    final snapshots = PlatformFileSnapshots();
    await snapshots.ready();
    final directory = await snapshots.directory('cache');
    final file = File('$directory/viewer-tab-restore.ply');
    await file.writeAsBytes(graphdecoFixture(), flush: true);
    final uri = await privateModelUri(file.path);
    final engine = Native3dgsEngineAdapter();
    final controller = ViewerController(
      ModelSourceAdapter(
        acquire: (source, cancellation, progress) async => Success(
          LocalModelRef(
            handle: uri,
            fileName: 'viewer-tab-restore.ply',
            releaseResource: () async {},
          ),
        ),
      ),
      engine,
      RendererIdentity(),
    );
    StreamSubscription<Native3dgsState>? rawEvents;
    Native3dgsState? nativeState;
    var surfaces = 0;
    late final GoRouter router;
    router = GoRouter(
      initialLocation: '/home',
      routes: [
        StatefulShellRoute.indexedStack(
          builder: (context, state, shell) {
            final selected = shell.currentIndex == 2;
            if (controller.visible != selected) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                unawaited(controller.setVisible(selected));
              });
            }
            return ListenableBuilder(
              listenable: controller,
              builder: (context, _) => MobileShell(
                navigationShell: shell,
                onUpload: () {},
                hideNavigation: selected && controller.fullscreen,
                onBeforeSwitch: () => controller.setFullscreen(false),
              ),
            );
          },
          branches: [
            for (final route in ['home', 'projects', 'viewer', 'account'])
              StatefulShellBranch(
                routes: [
                  GoRoute(
                    path: '/$route',
                    builder: (context, state) => route == 'viewer'
                        ? ViewerPage(
                            controller: controller,
                            onChooseCloud: () {},
                            surfaceBuilder: () {
                              surfaces++;
                              return Native3dgsView(
                                onCreated: (native) {
                                  rawEvents = native.states.listen((state) {
                                    nativeState = state;
                                    debugPrint(
                                      'Tab restore native: phase=${state.phase} '
                                      'request=${state.requestId} '
                                      'active=${state.activeRequestId} '
                                      'surface=${state.surfaceGeneration}',
                                    );
                                  });
                                  engine.attach(native);
                                },
                              );
                            },
                          )
                        : Scaffold(
                            appBar: AppBar(title: Text(route)),
                            body: ListView(
                              children: [
                                for (var i = 0; i < 8; i++) Text('$route $i'),
                              ],
                            ),
                          ),
                  ),
                ],
              ),
          ],
        ),
      ],
    );
    addTearDown(() async {
      await controller.close();
      await tester.pumpWidget(const SizedBox());
      engine.detach();
      final closed = await engine.close();
      if (closed case Failure<void>(:final error)) throw error;
      await rawEvents?.cancel();
      controller.dispose();
      await engine.dispose();
      router.dispose();
      if (await file.exists()) await file.delete();
    });
    await tester.pumpWidget(
      MaterialApp.router(
        theme: AppTheme.light,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('en'),
        routerConfig: router,
      ),
    );
    Future<void> tapTab(String tab) async {
      await tester.tap(find.byKey(Key('nav-$tab')));
      await tester.pumpAndSettle();
    }

    Future<void> ready({int afterGeneration = -1}) async {
      final deadline = DateTime.now().add(const Duration(seconds: 15));
      bool restored() =>
          controller.session.phase == ViewerPhase.ready &&
          nativeState?.phase == 'Ready' &&
          (nativeState?.surfaceGeneration ?? -1) > afterGeneration;
      while (!restored() && DateTime.now().isBefore(deadline)) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(controller.session.phase, ViewerPhase.ready);
      expect(nativeState?.phase, 'Ready');
      expect(nativeState!.surfaceGeneration, greaterThan(afterGeneration));
    }

    await tester.pumpAndSettle();
    await tapTab('projects');
    await tapTab('account');
    await tapTab('viewer');
    expect(surfaces, 0);
    expect(
      find.byKey(const Key('viewer-open-local')).hitTestable(),
      findsOneWidget,
    );
    await tapTab('account');
    await tapTab('viewer');
    expect(surfaces, 0);
    await controller.open(const LocalModelSource());
    await ready();
    final active = controller.session.active;
    final canvas = tester.getRect(find.byType(Native3dgsView));
    for (final tab in ['projects', 'account', 'home', 'projects', 'account']) {
      final generation = nativeState!.surfaceGeneration;
      await tapTab(tab);
      expect(controller.visible, isFalse);
      await tester.pump(const Duration(milliseconds: 300));
      await tapTab('viewer');
      await ready(afterGeneration: generation);
      expect(controller.session.active, same(active));
      expect(surfaces, 1);
      expect(tester.getRect(find.byType(Native3dgsView)), canvas);
      expect(
        find.byKey(const Key('viewer-open-local')).hitTestable(),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    }
    await controller.close();
    await tester.pumpAndSettle();
    expect(find.byType(Native3dgsView).hitTestable(), findsNothing);
    await tapTab('account');
    await tapTab('viewer');
    expect(surfaces, 1);
    expect(find.byType(Native3dgsView).hitTestable(), findsNothing);
    await controller.open(const LocalModelSource());
    await ready();
    expect(surfaces, 1);
  });
}
