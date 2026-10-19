import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xjicloud_mobile/app/create_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

/// Real API/OSS/worker path. An external device driver (or operator) must finish
/// the system SAF selection after choose-dataset-images is tapped. No fake files,
/// credentials, worker completion or platform-channel overrides are installed.
/// Supply acceptanceProjectId/acceptanceJobName and optional login credentials
/// only to this test target with an external --dart-define-from-file.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const projectId = String.fromEnvironment('acceptanceProjectId');
  const name = String.fromEnvironment('acceptanceJobName');
  const username = String.fromEnvironment('username'),
      password = String.fromEnvironment('password');
  testWidgets(
    'real SAF -> OSS -> queued -> worker -> private training output',
    (tester) async {
      expect(projectId, isNotEmpty);
      expect(name, isNotEmpty);
      final container = createContainer();
      await tester.pumpWidget(
        ProductApp(
          container: container,
          initialLocation: '/projects/$projectId',
        ),
      );
      Future<void> until(bool Function() ready, {int seconds = 60}) async {
        for (var i = 0; i < seconds * 4 && !ready(); i++) {
          await tester.pump(const Duration(milliseconds: 250));
        }
        expect(
          ready(),
          isTrue,
          reason: 'Real service/device state did not arrive; inspect the tracked job and worker.',
        );
      }

      await until(
        () =>
            container.sessions.session != null ||
            find.byKey(const Key('username')).evaluate().isNotEmpty,
      );
      if (container.sessions.session == null) {
        expect(username, isNotEmpty);
        expect(password, isNotEmpty);
        await tester.enterText(find.byKey(const Key('username')), username);
        await tester.enterText(find.byKey(const Key('password')), password);
        await tester.ensureVisible(find.byKey(const Key('auth-submit')));
        await tester.tap(find.byKey(const Key('auth-submit')));
        await until(() => container.sessions.session != null);
      }
      await until(
        () => find
            .byKey(const Key('project-upload-dataset'))
            .evaluate()
            .isNotEmpty,
      );
      final c = container.training!;
      expect(
        c.checkpoint,
        isNull,
        reason: 'Resolve any existing upload before this acceptance run.',
      );
      await c.refresh(projectId);
      expect(
        c.jobsFor(projectId).where((j) => j.name == name),
        isEmpty,
        reason: 'Use a unique externally supplied acceptance name.',
      );
      final l = AppLocalizations.of(
        tester.element(find.byKey(const Key('project-upload-dataset'))),
      );
      await tester.ensureVisible(
        find.byKey(const Key('project-upload-dataset')),
      );
      await tester.tap(find.byKey(const Key('project-upload-dataset')));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, l.uploadImages));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('choose-dataset-images')));
      await until(
        () => c.draft != null,
        seconds: 180,
      ); // external system-file picker driver
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('dataset-name-input')), name);
      await tester.ensureVisible(find.byKey(const Key('submit-dataset')));
      await tester.tap(find.byKey(const Key('submit-dataset')));
      await until(
        () =>
            !c.busy &&
            c.jobsFor(projectId).where((j) => j.name == name).isNotEmpty,
        seconds: 180,
      );
      final created = c
          .jobsFor(projectId)
          .where((j) => j.name == name)
          .toList();
      expect(created.length, 1);
      final id = created.single.id;
      expect(created.single.status, isNot(JobStatus.uploading));
      await until(
        () => c
            .jobsFor(projectId)
            .any(
              (j) =>
                  j.id == id &&
                  (j.status == JobStatus.completed ||
                      j.status == JobStatus.failed),
            ),
        seconds: 180,
      );
      final full = c.jobsFor(projectId).singleWhere((j) => j.id == id);
      expect(full.status, JobStatus.completed);
      expect(full.downloadUrl, isNotNull);
      await c.downloadOutput(full);
      expect(c.outputFor(id), isNotNull);
      expect(c.outputFor(id)!.sizeBytes, greaterThan(0));
      expect(tester.takeException(), isNull);
      // The parent device harness records and cleans this exact owned job.
      await tester.pumpWidget(const SizedBox());
    },
  );
}
