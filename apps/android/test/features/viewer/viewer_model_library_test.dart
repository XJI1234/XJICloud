import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_page.dart';
import 'package:xjicloud_mobile/features/viewer/presentation/viewer_controller.dart';

import 'viewer_session_test.dart' show Sources, Engine;

import 'package:xjicloud_mobile/app/viewer_model_library.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_assets_controller.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import '../project/project_fakes.dart';
import '../model_asset/chunk_upload_test.dart' show Repo, Files, Journal;
import '../model_asset/model_pages_test.dart' show WidgetCache;

class LibraryRepo extends Repo {
  final requests = <String>[];
  bool fail = false;
  Completer<Result<List<ModelAsset>>>? delayed;
  ModelAsset model(String id) => ModelAsset(
    id: id,
    projectId: id,
    fileName: '$id.ply',
    format: 'PLY',
    sizeBytes: 1024,
    version: 1,
    createdAt: DateTime.utc(2026),
    updatedAt: DateTime.utc(2026),
  );
  @override
  Future<Result<List<ModelAsset>>> list(
    String projectId,
    int generation,
  ) async {
    requests.add(projectId);
    return delayed != null
        ? delayed!.future
        : fail
        ? const Failure(DomainError('network'))
        : Success([model(projectId)]);
  }
}

void main() {
  for (final size in [
    const Size(360, 480),
    const Size(432, 320),
    const Size(1000, 800),
  ]) {
    testWidgets('library scrolls at 200% in $size including list error', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final workspace = WorkspaceController(
        FakeProjects(),
        MemoryWorkspace(),
        sessions,
      );
      final repo = LibraryRepo()..fail = true;
      final models = ModelAssetsController(
        repo,
        Files(),
        Journal(),
        WidgetCache(),
        sessions,
      );
      final viewer = ViewerController(Sources(), Engine(), sessions);
      workspace.synchronize();
      models.synchronize();
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(2)),
            child: child!,
          ),
          home: ViewerPage(
            controller: viewer,
            surfaceBuilder: () => throw StateError('Unexpected canvas'),
            onChooseCloud: () {},
            libraryBuilder: (context) => ViewerModelLibrary(
              workspace: workspace,
              models: models,
              onOpen: (_) {},
              onManageProjects: () {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(models.listError('a')?.code, 'network');
      expect(tester.takeException(), isNull);
      final local = find.byKey(const Key('viewer-open-local'));
      expect(local.hitTestable(), findsOneWidget);
      expect(tester.getSize(local).shortestSide, greaterThanOrEqualTo(48));
      repo.fail = false;
      await tester.tap(find.byTooltip('Refresh models'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('a.ply'));
      await tester.pumpAndSettle();
      expect(find.text('a.ply').hitTestable(), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      viewer.dispose();
      models.dispose();
      workspace.dispose();
      sessions.dispose();
    });
  }
  testWidgets(
    'library selects real projects, retries errors and clears late account data',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final workspace = WorkspaceController(
        FakeProjects(),
        MemoryWorkspace(),
        sessions,
      );
      final repo = LibraryRepo();
      final models = ModelAssetsController(
        repo,
        Files(),
        Journal(),
        WidgetCache(),
        sessions,
      );
      sessions.addListener(workspace.synchronize);
      sessions.addListener(models.synchronize);
      workspace.synchronize();
      models.synchronize();
      ModelAsset? chosen;
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: ViewerModelLibrary(
              workspace: workspace,
              models: models,
              onOpen: (model) => chosen = model,
              onManageProjects: () {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('a.ply'), findsOneWidget);
      await tester.tap(find.text('a.ply'));
      expect(chosen?.projectId, 'a');
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Project b').last);
      await tester.pumpAndSettle();
      expect(repo.requests, ['a', 'b']);
      expect(find.text('b.ply'), findsOneWidget);
      expect(
        workspace.activeProjectId,
        isNull,
        reason: 'Library browsing does not change the workspace',
      );
      repo.fail = true;
      await tester.tap(find.byTooltip('Refresh models'));
      await tester.pumpAndSettle();
      expect(models.listError('b')?.code, 'network');
      repo.fail = false;
      await tester.tap(find.byTooltip('Refresh models'));
      await tester.pumpAndSettle();
      expect(models.listError('b'), isNull);
      repo.delayed = Completer();
      await tester.tap(find.byTooltip('Refresh models'));
      await tester.pump();
      await sessions.invalidate();
      repo.delayed!.complete(Success([repo.model('secret')]));
      await tester.pumpAndSettle();
      expect(find.text('b.ply'), findsNothing);
      expect(find.text('secret.ply'), findsNothing);
      expect(find.text('No projects yet'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      sessions.removeListener(workspace.synchronize);
      sessions.removeListener(models.synchronize);
      models.dispose();
      workspace.dispose();
      sessions.dispose();
    },
  );
}
