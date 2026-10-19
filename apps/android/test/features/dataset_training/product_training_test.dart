import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/upload_controller.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import '../identity/session_routing_test.dart' show MemoryLocale;
import '../identity/auth_use_cases_test.dart' show FakeAuth;
import '../project/project_fakes.dart';
import 'submit_dataset_test.dart' show Jobs, Journal, Storage, Sources;
import 'job_policy_test.dart' show Events;

void main() {
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
