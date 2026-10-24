import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_controller.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_capabilities.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_page.dart';
import 'package:xjicloud_mobile/features/viewer/domain/model_source.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/presentation/error_text.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import '../identity/session_routing_test.dart' show MemoryLocale;
import '../identity/auth_use_cases_test.dart' show FakeAuth;
import '../project/project_fakes.dart';
import 'viewer_session_test.dart' show Sources, Engine;

class DelayedEngine extends Engine {
  final initialized = Completer<Result<ViewerCapabilities>>();
  @override
  Future<Result<ViewerCapabilities>> initialize() => initialized.future;
}

class RecreatedEngine extends Engine {
  final discoveries = <Completer<Result<ViewerCapabilities>>>[];
  @override
  Future<Result<ViewerCapabilities>> initialize() {
    final result = Completer<Result<ViewerCapabilities>>();
    discoveries.add(result);
    return result.future;
  }
}

void main() {
  test(
    'detached initialization cannot consume a new account pending source',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final sources = Sources(), engine = RecreatedEngine();
      final c = ViewerController(sources, engine, sessions);
      sessions.addListener(c.synchronize);
      c.synchronize();
      await c.open(const LocalModelSource());
      final old = c.initialize();
      await sessions.invalidate();
      c.surfaceRemoved();
      await sessions.activate(session('bob'));
      await c.open(const LocalModelSource());
      final current = c.initialize();
      engine.discoveries.first.complete(
        const Failure(DomainError('cancelled')),
      );
      await old;
      expect(sources.pending, isEmpty);
      engine.discoveries.last.complete(
        const Success(ViewerCapabilities(supported: true)),
      );
      await current;
      await Future<void>.delayed(Duration.zero);
      expect(sources.pending, hasLength(1));
      sources.pending.last.complete(const Failure(DomainError('cancelled')));
      await c.close();
      sessions.removeListener(c.synchronize);
      c.dispose();
      sessions.dispose();
    },
  );
  for (final operation in ['cancel', 'close']) {
    test(
      '$operation invalidates opens awaiting capability initialization and permits a later open',
      () async {
        final sessions = SessionCoordinator(MemoryStore());
        await sessions.activate(session());
        final sources = Sources(), engine = DelayedEngine();
        final c = ViewerController(sources, engine, sessions);
        await c.open(const LocalModelSource());
        final initializing = c.initialize();
        final waiting = c.open(const LocalModelSource());
        if (operation == 'cancel') {
          await c.cancel();
        } else {
          await c.close();
        }
        engine.initialized.complete(
          const Success(ViewerCapabilities(supported: true)),
        );
        await initializing;
        await waiting;
        expect(sources.pending, isEmpty);
        final later = c.open(const LocalModelSource());
        await Future<void>.delayed(Duration.zero);
        expect(sources.pending, hasLength(1));
        sources.pending.last.complete(const Failure(DomainError('cancelled')));
        await later;
        c.dispose();
        sessions.dispose();
      },
    );
  }
  testWidgets('empty model library never constructs an Android canvas', (
    tester,
  ) async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    final c = ViewerController(Sources(), Engine(), sessions);
    var surfaces = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('en'),
        home: ViewerPage(
          controller: c,
          surfaceBuilder: () {
            surfaces++;
            return const SizedBox();
          },
          onChooseCloud: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      surfaces,
      0,
      reason: 'An empty native Surface can retain another tab image',
    );
    expect(
      find.byKey(const Key('viewer-open-local')).hitTestable(),
      findsOneWidget,
    );
    expect(find.byTooltip('Fullscreen'), findsNothing);
    await tester.pumpWidget(const SizedBox());
    c.dispose();
    sessions.dispose();
  });
  test(
    'a queued initial download cannot block a newer model request',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final sources = Sources(), engine = Engine();
      final controller = ViewerController(sources, engine, sessions);
      await controller.open(const LocalModelSource());
      final initializing = controller.initialize();
      await Future<void>.delayed(Duration.zero);
      expect(sources.pending, hasLength(1));
      final newer = controller.open(const LocalModelSource());
      addTearDown(() async {
        await sessions.invalidate();
        await controller.close();
        for (final source in sources.pending) {
          if (!source.isCompleted) {
            source.complete(const Failure(DomainError('cancelled')));
          }
        }
        await initializing;
        await newer;
        controller.dispose();
        sessions.dispose();
      });
      await Future<void>.delayed(Duration.zero);
      expect(sources.pending, hasLength(2));
    },
  );
  test(
    'account replacement invalidates an open awaiting native initialization',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final sources = Sources(), engine = DelayedEngine();
      final c = ViewerController(sources, engine, sessions);
      sessions.addListener(c.synchronize);
      c.synchronize();
      final init = c.initialize();
      final opening = c.open(const LocalModelSource());
      await sessions.invalidate();
      await sessions.activate(session('bob'));
      engine.initialized.complete(
        const Success(ViewerCapabilities(supported: true)),
      );
      await init;
      await opening;
      expect(sources.pending, isEmpty);
      sessions.removeListener(c.synchronize);
      c.dispose();
      sessions.dispose();
    },
  );
  testWidgets(
    'formal four tabs retain one canvas through reflow, fullscreen Back and hidden Surface',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final sources = Sources(), engine = Engine();
      final viewer = ViewerController(sources, engine, sessions);
      var surfaces = 0;
      final container = AppContainer(
        sessions: sessions,
        auth: FakeAuth(),
        locales: MemoryLocale()..locale = 'en',
        closeTransport: () {},
        workspace: WorkspaceController(
          FakeProjects()..projects = [],
          MemoryWorkspace(),
          sessions,
        ),
        viewer: viewer,
        viewerSurface: () {
          surfaces++;
          return const ColoredBox(
            key: Key('real-viewer-boundary'),
            color: Colors.black,
          );
        },
      );
      await tester.pumpWidget(ProductApp(container: container));
      await tester.pumpAndSettle();
      final homeX = tester.getCenter(find.byKey(const Key('nav-home'))).dx;
      final projectsX = tester
          .getCenter(find.byKey(const Key('nav-projects')))
          .dx;
      final viewerX = tester.getCenter(find.byKey(const Key('nav-viewer'))).dx;
      final accountX = tester
          .getCenter(find.byKey(const Key('nav-account')))
          .dx;
      expect(
        homeX < projectsX && projectsX < viewerX && viewerX < accountX,
        isTrue,
      );
      await tester.tap(find.byKey(const Key('nav-viewer')));
      await tester.pumpAndSettle();
      expect(surfaces, 0);
      expect(viewer.visible, isTrue);
      await viewer.open(const LocalModelSource());
      await tester.pump();
      await tester.pump();
      expect(surfaces, 1);
      sources.pending.last.complete(
        Success(
          LocalModelRef(
            handle: 'test',
            fileName: 'A',
            releaseResource: () async {},
          ),
        ),
      );
      await tester.pump();
      await tester.pump();
      engine.ready(1);
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Fullscreen'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('nav-home')), findsNothing);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(viewer.fullscreen, isFalse);
      expect(
        viewer.visible,
        isTrue,
        reason: 'Back exits fullscreen before leaving Viewer',
      );
      expect(find.byKey(const Key('nav-home')), findsOneWidget);
      await tester.tap(find.byKey(const Key('nav-account')));
      await tester.pumpAndSettle();
      expect(viewer.visible, isFalse);
      await tester.tap(find.byKey(const Key('nav-viewer')));
      await tester.pumpAndSettle();
      expect(surfaces, 1);
      expect(viewer.visible, isTrue);
      tester.view.physicalSize = const Size(432, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpAndSettle();
      expect(surfaces, 1);
      expect(find.byKey(const Key('viewer-tools')), findsOneWidget);
      await sessions.invalidate();
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('username')), findsOneWidget);
      expect(sources.pending.every((request) => request.isCompleted), isTrue);
      expect(viewer.session.active, isNull);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'unsupported viewer has explicit result and disables file actions',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final engine = Engine()..supported = false;
      final c = ViewerController(Sources(), engine, sessions);
      final container = AppContainer(
        sessions: sessions,
        auth: FakeAuth(),
        locales: MemoryLocale()..locale = 'en',
        closeTransport: () {},
        workspace: WorkspaceController(
          FakeProjects()..projects = [],
          MemoryWorkspace(),
          sessions,
        ),
        viewer: c,
        viewerSurface: () => const SizedBox(),
      );
      await tester.pumpWidget(
        ProductApp(container: container, initialLocation: '/viewer'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('viewer-open-local')));
      await tester.pumpAndSettle();
      expect(c.session.phase, ViewerPhase.unsupported);
      expect(
        find.textContaining('Native viewer is unsupported'),
        findsOneWidget,
      );
      expect(
        tester
            .widget<IconButton>(find.byKey(const Key('viewer-open-local')))
            .onPressed,
        isNull,
      );
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'Phone tools and Pad overlay call real camera port at 200 percent text',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final source = Sources(),
          engine = Engine(),
          c = ViewerController(source, engine, sessions);
      var builds = 0;
      Widget app(double width) => MaterialApp(
        theme: AppTheme.light,
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: MediaQuery(
          data: MediaQueryData(
            size: Size(width, 800),
            textScaler: TextScaler.linear(2),
          ),
          child: ViewerPage(
            key: const Key('same-viewer'),
            controller: c,
            surfaceBuilder: () {
              builds++;
              return const SizedBox();
            },
            onChooseCloud: () {},
          ),
        ),
      );
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(432, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      await c.setVisible(true);
      await tester.pumpWidget(app(432));
      await tester.pumpAndSettle();
      final opened = c.open(const LocalModelSource());
      await tester.pump();
      source.pending.last.complete(
        Success(
          LocalModelRef(
            handle: 'test',
            fileName: 'A',
            releaseResource: () async {},
          ),
        ),
      );
      await opened;
      await tester.pump();
      engine.ready(1);
      await tester.pumpAndSettle();
      c.reportError(const DomainError('network'));
      await tester.pump();
      final text = tester.widget<Text>(
        find.text(
          errorText(
            AppLocalizations.of(tester.element(find.byType(ViewerPage))),
            const DomainError('network'),
          ),
        ),
      );
      expect(text.style?.color, Colors.white);
      await c.cancel();
      await tester.pump();
      await tester.tap(find.byKey(const Key('viewer-tools')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byTooltip('Free mode'));
      await tester.tap(find.byTooltip('Free mode'));
      await tester.pumpAndSettle();
      expect(c.session.free, isTrue);
      await tester.ensureVisible(find.byTooltip('Flip Y'));
      await tester.tap(find.byTooltip('Flip Y'));
      await tester.pumpAndSettle();
      expect(c.session.flipped, isTrue);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      tester.view.physicalSize = const Size(1000, 800);
      await tester.pumpWidget(app(1000));
      await tester.pumpAndSettle();
      expect(builds, 1);
      expect(find.byKey(const Key('viewer-tools')), findsNothing);
      expect(find.byTooltip('Flip Y'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      sessions.dispose();
    },
  );
}
