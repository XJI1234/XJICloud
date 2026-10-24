import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/app.dart';
import 'package:xjicloud_mobile/app/router.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_assets_controller.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_list.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/cache_page.dart';
import 'package:xjicloud_mobile/features/project/presentation/project_list_page.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/presentation/widgets/app_button.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

import '../features/identity/session_policy_test.dart'
    show MemoryStore, session;
import '../features/project/project_fakes.dart';
import '../features/model_asset/chunk_upload_test.dart'
    show Files, Journal, asset;
import '../features/model_asset/model_pages_test.dart'
    show DownloadRepo, WidgetCache;

void main() {
  testWidgets('model reading order retains metadata and contextual actions', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    try {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final c = ModelAssetsController(
        DownloadRepo(),
        Files(),
        Journal(),
        WidgetCache(),
        sessions,
      );
      c.synchronize();
      Widget app(Widget child) => MaterialApp(
        theme: AppTheme.light,
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: child is CachePage
            ? child
            : Scaffold(body: SingleChildScrollView(child: child)),
      );
      await tester.pumpWidget(
        app(ModelList(controller: c, projectId: 'p', onView: (_) {})),
      );
      await tester.pumpAndSettle();
      final metadata = find.bySemanticsLabel(
        RegExp(r'a\.ply\nPLY · 10 B · v1\nCreated: .+\nUpdated: .+'),
      );

      expect(metadata, findsOneWidget);
      expect(tester.getSemantics(metadata).value, 'm');
      for (final action in [
        'View model',
        'Download to private cache',
        'Delete cloud model',
      ]) {
        final control = find.bySemanticsLabel('$action: a.ply');
        expect(control, findsOneWidget);
        await tester.ensureVisible(control);
        await tester.pumpAndSettle();
        final node = tester.getSemantics(control);
        expect(node.rect.width, greaterThanOrEqualTo(48));
        expect(node.rect.height, greaterThanOrEqualTo(48));
      }
      await tester.ensureVisible(
        find.bySemanticsLabel('Download to private cache: a.ply'),
      );
      await tester.tap(
        find.bySemanticsLabel('Download to private cache: a.ply'),
      );
      await tester.pumpAndSettle();
      expect(c.cached(asset), isTrue);
      expect(
        find.bySemanticsLabel(RegExp(r'a\.ply\nPLY.*\nCached', dotAll: true)),
        findsOneWidget,
      );
      await tester.pumpWidget(app(CachePage(controller: c)));
      await tester.pumpAndSettle();
      final remove = find.bySemanticsLabel('Remove cached copy: a.ply');
      expect(remove, findsOneWidget);
      await tester.tap(remove);
      await tester.pumpAndSettle();
      expect(c.cached(asset), isFalse);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      sessions.dispose();
    } finally {
      semantics.dispose();
    }
  });

  for (final width in [599.0, 600.0, 839.0, 840.0]) {
    testWidgets(
      'accessible 200% product form/navigation at ${width}dp with keyboard and reduced motion',
      (tester) async {
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = Size(width, 1000);
        addTearDown(tester.view.reset);
        final semantics = tester.ensureSemantics();
        try {
          final sessions = SessionCoordinator(MemoryStore());
          await sessions.activate(session());
          final workspace = WorkspaceController(
            FakeProjects(),
            MemoryWorkspace(),
            sessions,
          );
          workspace.synchronize();
          await workspace.refresh();
          final router = createAppRouter(
            initialLocation: '/projects',
            routes: [
              createMobileShellRoute(
                destinations: ShellDestinations(
                  home: (_, _) => const Text('Home content'),
                  projects: (_, _) => ProjectListPage(controller: workspace),
                  viewer: (_, _) => const Text('Viewer content'),
                  account: (_, _) => const Text('Account content'),
                ),
                onUpload: (_) {},
              ),
            ],
          );
          await tester.pumpWidget(
            XjiCloudApp(
              router: router,
              locale: const Locale('en'),
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: TextScaler.linear(2),
                  disableAnimations: true,
                ),
                child: child!,
              ),
            ),
          );
          await tester.pumpAndSettle();
          for (final id in ['home', 'projects', 'viewer', 'account']) {
            final control = find.byKey(Key('nav-$id'));
            final size = tester.getSize(control);
            expect(size.width, greaterThanOrEqualTo(48));
            expect(size.height, greaterThanOrEqualTo(48));
          }
          await tester.scrollUntilVisible(
            find.byKey(const Key('create-project')),
            200,
            scrollable: find.descendant(
              of: find.byType(ListView).first,
              matching: find.byType(Scrollable),
            ),
          );
          await tester.pumpAndSettle();
          await tester.ensureVisible(find.byKey(const Key('create-project')));
          await tester.pumpAndSettle();
          await tester.tap(find.byKey(const Key('create-project')));
          await tester.pumpAndSettle();
          await tester.enterText(
            find.byKey(const Key('project-name-input')),
            'Accessible draft',
          );
          tester.view.viewInsets = const FakeViewPadding(bottom: 300);
          await tester.pumpAndSettle();
          await tester.ensureVisible(find.byKey(const Key('save-project')));
          await tester.pumpAndSettle();
          expect(
            tester.getBottomLeft(find.byKey(const Key('save-project'))).dy,
            lessThanOrEqualTo(700),
          );
          await tester.binding.handlePopRoute();
          await tester.pumpAndSettle();
          expect(find.text('Discard changes?'), findsOneWidget);
          await tester.tap(find.text('Keep editing'));
          await tester.pumpAndSettle();
          expect(find.text('Accessible draft'), findsOneWidget);
          expect(tester.takeException(), isNull);
          await tester.pumpWidget(const SizedBox());
          router.dispose();
          workspace.dispose();
          sessions.dispose();
        } finally {
          semantics.dispose();
        }
      },
    );
  }

  testWidgets('reduced motion keeps an accessible pressed button stationary', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    try {
      var calls = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          home: MediaQuery(
            data: const MediaQueryData(disableAnimations: true),
            child: Scaffold(
              body: AppButton(label: 'Submit', onPressed: () => calls++),
            ),
          ),
        ),
      );
      final original = tester.getRect(find.byType(TextButton));
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Submit')),
      );
      await tester.pump();
      expect(tester.getRect(find.byType(TextButton)), original);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(calls, 1);
      expect(tester.takeException(), isNull);
    } finally {
      semantics.dispose();
    }
  });
}
