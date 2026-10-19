import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xjicloud_mobile/app/create_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/features/viewer/domain/viewer_capabilities.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

/// Real product/API/private-cache/SDK journey. Supply an external JSON via
/// --dart-define-from-file containing API_BASE_URL, username, password,
/// secondUsername, secondPassword, acceptanceProjectName (unique per run),
/// acceptanceProjectId and acceptanceModelId. The fixture project/model must
/// belong to account one, contain a renderable binary Gaussian PLY/SPZ and be
/// absent from account two. Use isolated acceptance accounts: this test clears
/// account one's private model cache. No transport, storage or renderer mocks.
/// Registration/captcha, SAF uploads and Worker completion use separate device
/// journeys; this test never manufactures those states. On failure the printed
/// exact owned project ID allows the operator to clean that record only.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const username = String.fromEnvironment('username'),
      password = String.fromEnvironment('password'),
      secondUsername = String.fromEnvironment('secondUsername'),
      secondPassword = String.fromEnvironment('secondPassword'),
      name = String.fromEnvironment('acceptanceProjectName'),
      fixtureProject = String.fromEnvironment('acceptanceProjectId'),
      fixtureModel = String.fromEnvironment('acceptanceModelId');

  testWidgets(
    'real project edit, cloud native view, cache, logout and account isolation',
    (tester) async {
      for (final value in [
        username,
        password,
        secondUsername,
        secondPassword,
        name,
        fixtureProject,
        fixtureModel,
      ]) {
        expect(
          value,
          isNotEmpty,
          reason: 'Supply all external mobile journey defines; see this test header.',
        );
      }
      expect(secondUsername, isNot(username));
      final container = createContainer();
      final semantics = tester.ensureSemantics();
      final workspace = container.workspace!,
          models = container.modelAssets!,
          viewer = container.viewer!;
      await tester.pumpWidget(
        ProductApp(container: container, initialLocation: '/account'),
      );
      try {
        Future<void> until(bool Function() ready, {int seconds = 60}) async {
          for (var i = 0; i < seconds * 4 && !ready(); i++) {
            await tester.pump(const Duration(milliseconds: 250));
          }
          expect(
            ready(),
            isTrue,
            reason: 'Real mobile journey state timed out; inspect API, fixture and device.',
          );
          await tester.pumpAndSettle();
        }

        Future<void> tap(Finder finder) async {
          expect(finder, findsOneWidget);
          await tester.ensureVisible(finder);
          await tester.pumpAndSettle();
          await tester.tap(finder);
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
        }

        Future<void> reveal(Finder target, Finder list) async {
          for (var i = 0; i < 40 && target.evaluate().isEmpty; i++) {
            await tester.drag(list, const Offset(0, -250));
            await tester.pumpAndSettle();
          }
          expect(target, findsOneWidget);
          await tester.ensureVisible(target);
          await tester.pumpAndSettle();
        }

        Future<void> account() async {
          await tap(find.byKey(const Key('nav-account')));
          if (find.byKey(const Key('account-logout')).evaluate().isEmpty &&
              find.byType(BackButton).evaluate().isNotEmpty) {
            await tap(find.byType(BackButton));
          }
        }

        Future<void> logout() async {
          await account();
          await tap(find.byKey(const Key('account-logout')));
          await until(
            () => find.byKey(const Key('username')).evaluate().isNotEmpty,
          );
          expect(container.sessions.session, isNull);
          expect(workspace.activeProjectId, isNull);
          expect(models.cacheEntries, isEmpty);
          expect(viewer.session.active, isNull);
        }

        Future<void> login(String user, String pass) async {
          await tester.enterText(find.byKey(const Key('username')), user);
          await tester.enterText(find.byKey(const Key('password')), pass);
          // The real need-captcha lookup is debounced. If captcha is required,
          // the operator must use fresh acceptance accounts rather than bypass it.
          await tester.pump(const Duration(milliseconds: 600));
          await tap(find.byKey(const Key('auth-submit')));
          await until(
            () =>
                container.sessions.session?.username == user &&
                !workspace.loading,
          );
        }

        await until(
          () =>
              container.sessions.session != null ||
              find.byKey(const Key('username')).evaluate().isNotEmpty,
        );
        if (container.sessions.session != null) await logout();
        await login(username, password);
        final firstUserId = container.sessions.session!.userId;
        expect(
          workspace.projects.any(
            (p) => p.name == name || p.name == '$name edited',
          ),
          isFalse,
          reason: 'Use a new acceptanceProjectName; do not overwrite an earlier project.',
        );
        await tap(find.byKey(const Key('nav-projects')));
        await reveal(
          find.byKey(const Key('create-project')),
          find.byType(ListView).first,
        );
        await tap(find.byKey(const Key('create-project')));
        await tester.enterText(
          find.byKey(const Key('project-name-input')),
          name,
        );
        await tester.enterText(
          find.byKey(const Key('project-description-input')),
          'Mobile acceptance owned resource',
        );
        await tap(find.byKey(const Key('save-project')));
        await until(
          () =>
              !workspace.busy &&
              workspace.projects.where((p) => p.name == name).length == 1,
        );
        final ownedId = workspace.projects
            .singleWhere((p) => p.name == name)
            .id;
        debugPrint('Task8 exact owned project ID: $ownedId');
        await reveal(
          find.byKey(Key('project-$ownedId')),
          find.byType(ListView).first,
        );
        await tap(find.byKey(Key('project-$ownedId')));
        await tap(find.byKey(const Key('edit-project')));
        await tester.enterText(
          find.byKey(const Key('project-name-input')),
          '$name edited',
        );
        await tap(find.byKey(const Key('save-project')));
        await until(
          () =>
              workspace.projectById(ownedId)?.name == '$name edited' &&
              !workspace.busy,
        );
        expect(find.text('$name edited'), findsWidgets);

        // Return via the actual tab, then choose the account-owned fixture.
        await tap(find.byKey(const Key('nav-home')));
        await tap(find.byKey(const Key('nav-projects')));
        if (find.byKey(Key('project-$fixtureProject')).evaluate().isEmpty &&
            find.byType(BackButton).evaluate().isNotEmpty) {
          await tap(find.byType(BackButton));
        }
        await reveal(
          find.byKey(Key('project-$fixtureProject')),
          find.byType(ListView).first,
        );
        await tap(find.byKey(Key('project-$fixtureProject')));
        await until(
          () =>
              !models.loading(fixtureProject) &&
              models.modelsFor(fixtureProject).any((m) => m.id == fixtureModel),
        );
        final model = models
            .modelsFor(fixtureProject)
            .singleWhere((m) => m.id == fixtureModel);
        final detail = find.byKey(Key('project-detail-$fixtureProject'));
        final download = find.bySemanticsLabel(
          '${AppLocalizations.of(tester.element(detail)).modelDownload}: ${model.fileName}',
        );
        await reveal(download, detail);
        await tap(download);
        await until(
          () => !models.downloading(model.id) && models.cached(model),
        );
        final open = find.bySemanticsLabel(
          '${AppLocalizations.of(tester.element(detail)).viewerOpen}: ${model.fileName}',
        );
        await reveal(open, detail);
        await tap(open);
        await until(
          () =>
              viewer.session.phase == ViewerPhase.ready ||
              viewer.session.phase == ViewerPhase.failed ||
              viewer.session.phase == ViewerPhase.unsupported,
          seconds: 120,
        );
        expect(
          viewer.session.phase,
          ViewerPhase.ready,
          reason: 'Actual SDK must render the supplied binary fixture; unsupported/error is not a pass.',
        );
        expect(viewer.session.active?.fileName, model.fileName);
        expect(models.cached(model), isTrue);
        final l = AppLocalizations.of(
          tester.element(find.byKey(const Key('nav-viewer'))),
        );
        await tap(find.byTooltip(l.viewerFullscreen));
        expect(find.byKey(const Key('nav-account')), findsNothing);
        await tester.binding.handlePopRoute();
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('nav-account')), findsOneWidget);
        await tap(find.byTooltip(l.viewerClose));
        await until(() => viewer.session.active == null);
        await account();
        await tap(find.byKey(const Key('account-cache')));
        await until(
          () =>
              !models.cacheBusy &&
              models.cacheEntries.any((e) => e.modelId == fixtureModel),
        );
        await tap(find.byKey(const Key('clear-model-cache')));
        await until(() => !models.cacheBusy && models.cacheEntries.isEmpty);
        expect(models.cacheBytes, 0);
        expect(models.cached(model), isFalse);
        await logout();

        await login(secondUsername, secondPassword);
        expect(container.sessions.session!.userId, isNot(firstUserId));
        await until(() => !workspace.loading);
        expect(
          workspace.projects.any(
            (p) => p.id == ownedId || p.id == fixtureProject,
          ),
          isFalse,
        );
        expect(
          workspace.recentProjects.any(
            (p) => p.id == ownedId || p.id == fixtureProject,
          ),
          isFalse,
        );
        expect(models.modelsFor(fixtureProject), isEmpty);
        expect(viewer.session.active, isNull);
        await account();
        await tap(find.byKey(const Key('account-cache')));
        await until(() => !models.cacheBusy);
        expect(models.cacheEntries, isEmpty);
        expect(find.text(model.fileName), findsNothing);
        await logout();

        // Delete only the new owned project, using the normal confirmation UI.
        await login(username, password);
        await tap(find.byKey(const Key('nav-projects')));
        if (find.byType(BackButton).evaluate().isNotEmpty) {
          await tap(find.byType(BackButton));
        }
        await reveal(
          find.byKey(Key('project-$ownedId')),
          find.byType(ListView).first,
        );
        await tap(find.byKey(Key('project-$ownedId')));
        await tap(find.byKey(const Key('delete-project')));
        final dialog = find.byType(AlertDialog);
        expect(dialog, findsOneWidget);
        expect(
          find.descendant(
            of: dialog,
            matching: find.textContaining('$name edited'),
          ),
          findsOneWidget,
        );
        await tap(
          find.descendant(of: dialog, matching: find.byType(FilledButton)),
        );
        await until(
          () => !workspace.busy && workspace.projectById(ownedId) == null,
        );
        expect(workspace.projectById(fixtureProject), isNotNull);
        await logout();
        expect(tester.takeException(), isNull);
      } finally {
        try {
          await viewer.close();
          await tester.pumpWidget(const SizedBox());
        } finally {
          semantics.dispose();
        }
      }
    },
  );
}
