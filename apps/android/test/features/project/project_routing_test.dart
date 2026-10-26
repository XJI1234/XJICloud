import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import '../identity/session_routing_test.dart' show MemoryLocale;
import '../identity/auth_use_cases_test.dart' show FakeAuth;
import 'project_fakes.dart';

void main() {
  test('controlled redirect accepts only defined internal project routes', () {
    expect(controlledTarget('/home'), '/home');
    expect(controlledTarget('/viewer'), '/viewer');
    expect(controlledTarget('/viewer?token=secret'), '/account');
    expect(controlledTarget('/projects'), '/projects');
    expect(
      controlledTarget('/projects/3d1fa7eb-3d29-404b-b6c9-274f77e52de5'),
      '/projects/3d1fa7eb-3d29-404b-b6c9-274f77e52de5',
    );
    for (final path in [
      'https://evil.test/home',
      '//evil.test/projects',
      '/projects/../admin',
      '/projects/a?token=x',
    ]) {
      expect(controlledTarget(path), '/account');
    }
  });
  testWidgets(
    'real home/projects/account navigation and create edit current recent delete',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final repo = FakeProjects()..projects = [];
      final store = MemoryWorkspace();
      final workspace = WorkspaceController(repo, store, sessions);
      final locales = MemoryLocale()..locale = 'en';
      final container = AppContainer(
        sessions: sessions,
        auth: FakeAuth(),
        locales: locales,
        closeTransport: () {},
        workspace: workspace,
      );
      await tester.pumpWidget(ProductApp(container: container));
      await tester.pumpAndSettle();
      // Restore uses the activated session persisted above.
      expect(find.text('Create your first project'), findsOneWidget);
      await tester.tap(find.byKey(const Key('create-project')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('project-name-input')),
        'My site',
      );
      await tester.tap(find.byKey(const Key('save-project')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('nav-projects')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('project-new')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('set-current-project')));
      await tester.pumpAndSettle();
      expect(workspace.activeProjectId, 'new');
      await tester.tap(find.byKey(const Key('edit-project')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('project-name-input')),
        'Updated site',
      );
      await tester.tap(find.byKey(const Key('save-project')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('nav-home')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('recent-new')), findsOneWidget);
      await tester.tap(find.byKey(const Key('recent-new')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('delete-project')));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Delete project'));
      await tester.pumpAndSettle();
      expect(workspace.activeProjectId, isNull);
      expect(workspace.recentProjects, isEmpty);
      await tester.tap(find.byKey(const Key('nav-account')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('account-username')), findsOneWidget);
      await sessions.invalidate();
      await tester.pumpAndSettle();
      expect(workspace.projects, isEmpty);
      expect(workspace.userId, isNull);
      expect(find.byKey(const Key('fusion-submit')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
