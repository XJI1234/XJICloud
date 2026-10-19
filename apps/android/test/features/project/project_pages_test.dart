import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:xjicloud_mobile/app/app.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/project/presentation/project_list_page.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'project_fakes.dart';

void main() {
  for (final width in [390.0, 1280.0]) {
    testWidgets(
      'project selection shows ${width < 840 ? 'Phone detail route' : 'Pad same selection split'} and survives resize',
      (tester) async {
        tester.view.physicalSize = Size(width, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final sessions = SessionCoordinator(MemoryStore());
        await sessions.activate(session());
        final controller = WorkspaceController(
          FakeProjects(),
          MemoryWorkspace(),
          sessions,
        );
        controller.synchronize();
        await controller.refresh();
        final router = GoRouter(
          initialLocation: '/projects',
          routes: [
            GoRoute(
              path: '/projects',
              builder: (context, state) =>
                  ProjectListPage(controller: controller),
              routes: [
                GoRoute(
                  path: ':id',
                  builder: (context, state) => ProjectListPage(
                    controller: controller,
                    projectId: state.pathParameters['id'],
                  ),
                ),
              ],
            ),
          ],
        );
        await tester.pumpWidget(
          XjiCloudApp(router: router, locale: const Locale('en')),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const Key('project-a')));
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('project-detail-a')), findsOneWidget);
        expect(controller.selectedProjectId, 'a');
        expect(router.routeInformationProvider.value.uri.path, '/projects/a');
        expect(
          find.byKey(const Key('project-a')),
          width < 840 ? findsNothing : findsOneWidget,
        );
        for (final resizedWidth in [599.0, 600.0, 839.0, 840.0]) {
          tester.view.physicalSize = Size(resizedWidth, 900);
          await tester.pumpAndSettle();
          expect(find.byKey(const Key('project-detail-a')), findsOneWidget);
          expect(
            find.byKey(const Key('project-a')),
            resizedWidth < 840 ? findsNothing : findsOneWidget,
          );
        }
        tester.view.physicalSize = const Size(599, 900);
        await tester.pumpAndSettle();
        // An unsaved draft survives width changes and failed mutation.
        await tester.tap(find.byKey(const Key('edit-project')));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const Key('project-name-input')),
          'Draft title',
        );
        tester.view.physicalSize = const Size(1000, 900);
        await tester.pumpAndSettle();
        expect(find.text('Draft title'), findsOneWidget);
        (controller.repository as FakeProjects).fail = true;
        await tester.tap(find.byKey(const Key('save-project')));
        await tester.pumpAndSettle();
        expect(find.text('Draft title'), findsOneWidget);
        expect(
          find.text(
            'Access denied. Sign in again or contact your administrator.',
          ),
          findsWidgets,
        );
        expect(tester.takeException(), isNull);
        await tester.binding.handlePopRoute();
        await tester.pumpAndSettle();
        expect(find.text('Discard changes?'), findsOneWidget);
        await tester.tap(find.text('Keep editing'));
        await tester.pumpAndSettle();
        expect(find.text('Draft title'), findsOneWidget);
        await tester.pumpWidget(const SizedBox());
        router.dispose();
        controller.dispose();
        sessions.dispose();
      },
    );
  }
  testWidgets('200 percent text has no overflow on narrow landscape', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 360);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    final c = WorkspaceController(FakeProjects(), MemoryWorkspace(), sessions);
    c.synchronize();
    await c.refresh();
    final router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) =>
              ProjectListPage(controller: c, projectId: 'a'),
        ),
      ],
    );
    await tester.pumpWidget(
      XjiCloudApp(
        router: router,
        locale: const Locale('en'),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(2)),
          child: child!,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('project-detail-a')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    router.dispose();
    c.dispose();
    sessions.dispose();
  });
}
