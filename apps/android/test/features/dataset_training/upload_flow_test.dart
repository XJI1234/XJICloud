import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/upload_controller.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/upload_flow_page.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/training_job_panel.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'submit_dataset_test.dart' show Jobs, Journal, Storage, Sources;
import 'job_policy_test.dart' show Events;

void main() {
  testWidgets('covered route releases watcher; returning gets fresh state', (
    tester,
  ) async {
    final s = SessionCoordinator(MemoryStore());
    await s.activate(session());
    final repo = Jobs(),
        events = Events(),
        c = UploadController(repo, Sources(), Storage(), Journal(), events, s);
    c.synchronize();
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SingleChildScrollView(
            child: TrainingJobPanel(controller: c, projectId: 'p'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(repo.gets, 1);
    navigator.currentState!.push(
      MaterialPageRoute<void>(
        builder: (_) => const Scaffold(body: Text('another page')),
      ),
    );
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 30));
    expect(repo.gets, 1);
    navigator.currentState!.pop();
    await tester.pumpAndSettle();
    expect(repo.gets, 2);
    await tester.pumpWidget(const SizedBox());
    c.dispose();
    s.dispose();
  });
  testWidgets(
    'real upload path previews chosen sources and queues fixed project',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      addTearDown(sessions.dispose);
      final repo = Jobs(),
          c = UploadController(
            repo,
            Sources(),
            Storage(),
            Journal(),
            Events(),
            sessions,
          );
      c.synchronize();
      addTearDown(c.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: UploadFlowPage(
            controller: c,
            projectId: 'p',
            projectName: 'Fixed project',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Fixed project'), findsOneWidget);
      await tester.tap(find.byKey(const Key('choose-dataset-images')));
      await tester.pumpAndSettle();
      expect(find.textContaining('0001.jpg'), findsOneWidget);
      expect(repo.creates, 0);
      await tester.enterText(
        find.byKey(const Key('dataset-name-input')),
        'scan',
      );
      await tester.ensureVisible(find.byKey(const Key('submit-dataset')));
      await tester.tap(find.byKey(const Key('submit-dataset')));
      await tester.pumpAndSettle();
      expect(repo.creates, 1);
      expect(repo.completes, 1);
      expect(find.text('Queued'), findsOneWidget);
      expect(c.checkpoint, isNull);
      expect(find.textContaining('https://'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
      await sessions.invalidate();
    },
  );
}
