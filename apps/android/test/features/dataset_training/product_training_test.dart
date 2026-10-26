import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/upload_controller.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import '../identity/session_routing_test.dart' show MemoryLocale;
import '../identity/auth_use_cases_test.dart' show FakeAuth;
import '../project/project_fakes.dart';
import 'submit_dataset_test.dart' show Jobs, Journal, Storage, Sources;
import 'job_policy_test.dart' show Events;
import 'resume_dataset_test.dart' show EmptyPicker;

void main() {
  testWidgets(
    'home-only startup initializes private storage and displays failure',
    (tester) async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      var initialized = 0;
      final container = AppContainer(
        sessions: s,
        auth: FakeAuth(),
        locales: MemoryLocale()..locale = 'en',
        closeTransport: () {},
        workspace: WorkspaceController(FakeProjects(), MemoryWorkspace(), s),
        initializeStorage: () async {
          initialized++;
          return const Failure(DomainError('storage'));
        },
      );
      await tester.pumpWidget(ProductApp(container: container));
      await tester.pumpAndSettle();
      expect(initialized, 1);
      expect(
        find.text(
          'Unable to securely read or clear your session. Retry; if this continues, clear app data in system settings.',
        ),
        findsOneWidget,
      );
      expect(find.byKey(const Key('nav-home')), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'cancel project A picker then enter upload from real project B page',
    (tester) async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      final sources = EmptyPicker();
      final c = UploadController(
            Jobs(),
            sources,
            Storage(),
            Journal(),
            Events(),
            s,
          ),
          workspace = WorkspaceController(FakeProjects(), MemoryWorkspace(), s);
      final container = AppContainer(
        sessions: s,
        auth: FakeAuth(),
        locales: MemoryLocale()..locale = 'en',
        closeTransport: () {},
        workspace: workspace,
        training: c,
      );
      await tester.pumpWidget(
        ProductApp(container: container, initialLocation: '/projects/a'),
      );
      await tester.pumpAndSettle();
      Future<void> enterUpload() async {
        await tester.ensureVisible(
          find.byKey(const Key('project-upload-dataset')),
        );
        await tester.tap(find.byKey(const Key('project-upload-dataset')));
        await tester.pumpAndSettle();
        await tester.tapAt(
          tester.getTopLeft(
                find.descendant(
                  of: find.byType(BottomSheet),
                  matching: find.widgetWithText(
                    FilledButton,
                    'Upload images and train',
                  ),
                ),
              ) +
              const Offset(32, 16),
        );
        await tester.pumpAndSettle();
      }

      await enterUpload();
      await tester.tap(find.byKey(const Key('choose-dataset-images')));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('nav-projects')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('project-b')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('project-upload-dataset')),
      );
      await tester.tap(find.byKey(const Key('project-upload-dataset')));
      await tester.pumpAndSettle();
      // A clean preparation must offer the chooser again, with B preselected.
      expect(find.text('Fixed destination project'), findsOneWidget);
      final selected = tester.widget<ListTile>(
        find.widgetWithText(ListTile, 'Project b'),
      );
      expect(selected.selected, isTrue);
      await tester.tapAt(
        tester.getTopLeft(
              find.descendant(
                of: find.byType(BottomSheet),
                matching: find.widgetWithText(
                  FilledButton,
                  'Upload images and train',
                ),
              ),
            ) +
            const Offset(32, 16),
      );
      await tester.pumpAndSettle();
      expect(find.text('Fixed destination project: Project b'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'ordinary app central chooser opens fixed-project real image flow',
    (tester) async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      final c = UploadController(
            Jobs(),
            Sources(),
            Storage(),
            Journal(),
            Events(),
            s,
          ),
          workspace = WorkspaceController(FakeProjects(), MemoryWorkspace(), s);
      final container = AppContainer(
        sessions: s,
        auth: FakeAuth(),
        locales: MemoryLocale()..locale = 'en',
        closeTransport: () {},
        workspace: workspace,
        training: c,
      );
      await tester.pumpWidget(ProductApp(container: container));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('central-upload-model')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('upload-dataset-choice')));
      await tester.pumpAndSettle();
      expect(find.text('Fixed destination project'), findsOneWidget);
      await tester.tap(find.byType(ListTile).first);
      await tester.pumpAndSettle();
      await tester.tap(
        find.widgetWithText(FilledButton, 'Upload images and train'),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('choose-dataset-images')), findsOneWidget);
      await tester.tap(find.byKey(const Key('choose-dataset-images')));
      await tester.pumpAndSettle();
      expect(c.draft!.files.length, 2);
      await tester.pumpWidget(const SizedBox());
      expect(tester.takeException(), isNull);
    },
  );
}
