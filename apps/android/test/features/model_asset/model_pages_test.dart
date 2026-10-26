import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_list.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/cache_page.dart';
import 'package:xjicloud_mobile/features/model_asset/presentation/model_assets_controller.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_cache_port.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_asset.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_revision.dart';

import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';

import '../project/project_fakes.dart';
import '../identity/session_routing_test.dart' show MemoryLocale;
import '../identity/auth_use_cases_test.dart' show FakeAuth;

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'chunk_upload_test.dart' show Repo, Files, Journal, asset;

class DownloadRepo extends Repo {
  int deletedModels = 0;
  @override
  Future<Result<void>> delete(String id, int generation) async {
    deletedModels++;
    return const Success(null);
  }

  @override
  Future<Result<void>> downloadToFile(
    String id,
    int generation,
    Future<void> Function(Stream<List<int>>) consume,
    Cancellation cancellation,
  ) async {
    await consume(Stream.value(List.filled(10, 1)));
    return const Success(null);
  }
}

class CancelledPicker extends Files {
  int calls = 0;
  @override
  Future<Result<LocalModelRef?>> choose(Cancellation cancellation) async {
    calls++;
    return const Success(null);
  }
}

class TimestampRepo extends Repo {
  final edited = ModelAsset(
    id: asset.id,
    projectId: asset.projectId,
    fileName: asset.fileName,
    format: asset.format,
    sizeBytes: asset.sizeBytes,
    version: 2,
    createdAt: DateTime.utc(2026, 1, 1),
    updatedAt: DateTime.utc(2026, 2, 2),
  );
  @override
  Future<Result<List<ModelAsset>>> list(
    String projectId,
    int generation,
  ) async => Success([edited]);
}

Widget app(Widget child) => MaterialApp(
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: child is CachePage
      ? child
      : Scaffold(body: SingleChildScrollView(child: child)),
);

class WidgetCache implements ModelCachePort {
  final stored = <String, ModelAsset>{};
  @override
  Future<Result<List<ModelCacheEntry>>> entries(String user) async => Success([
    for (final a in stored.values)
      ModelCacheEntry(
        key: ModelRevision.of(user, a).key,
        modelId: a.id,
        projectId: a.projectId,
        fileName: a.fileName,
        sizeBytes: a.sizeBytes,
        inUse: false,
      ),
  ]);
  @override
  Future<Result<int>> evict(String user, String key) async {
    final a = stored.values
        .where((a) => ModelRevision.of(user, a).key == key)
        .firstOrNull;
    if (a == null) return const Success(0);
    stored.remove(a.id);
    return Success(a.sizeBytes);
  }

  CacheLease lease(ModelAsset a) => CacheLease(
    LocalModelRef(
      id: a.id,
      fileName: a.fileName,
      sizeBytes: a.sizeBytes,
      fingerprint: 'cache',
    ),
    () {},
  );
  @override
  Future<Result<CacheLease?>> acquire(String user, ModelAsset a) async =>
      Success(stored.containsKey(a.id) ? lease(a) : null);
  @override
  Future<Result<CacheLease>> store(
    String user,
    ModelAsset a,
    Future<Result<void>> Function(Future<void> Function(Stream<List<int>>))
    download,
    Cancellation token,
    bool Function() current, {
    void Function(int, int)? onProgress,
  }) async {
    await download((s) async {
      await s.drain<void>();
    });
    stored[a.id] = a;
    return Success(lease(a));
  }

  @override
  Future<Result<int>> clear(String user) async {
    final n = stored.values.fold<int>(0, (n, a) => n + a.sizeBytes);
    stored.clear();
    return Success(n);
  }

  @override
  Future<Result<int>> size(String user) async =>
      Success(stored.values.fold<int>(0, (n, a) => n + a.sizeBytes));
  @override
  Future<Result<void>> invalidate(String user, String id) async {
    stored.remove(id);
    return const Success(null);
  }

  @override
  Future<Result<void>> retain(
    String user,
    String project,
    List<ModelAsset> assets,
  ) async => const Success(null);
}

void main() {
  testWidgets('model row distinguishes updated time from creation evidence', (
    tester,
  ) async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    final repo = TimestampRepo();
    final c = ModelAssetsController(
      repo,
      Files(),
      Journal(),
      WidgetCache(),
      sessions,
    );
    c.synchronize();
    await tester.pumpWidget(app(ModelList(controller: c, projectId: 'p')));
    await tester.pumpAndSettle();
    final date = DateFormat.yMd('en').add_Hms();
    expect(
      find.text('Updated: ${date.format(repo.edited.updatedAt.toLocal())}'),
      findsOneWidget,
    );
    expect(
      find.text('Created: ${date.format(repo.edited.createdAt.toLocal())}'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    c.dispose();
    sessions.dispose();
  });
  testWidgets(
    'project model user flow upload known completion list refresh and cache download clear',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final repo = DownloadRepo();
      final controller = ModelAssetsController(
        repo,
        Files(),
        Journal(),
        WidgetCache(),
        sessions,
      );
      controller.synchronize();
      await tester.pumpWidget(
        app(ModelList(controller: controller, projectId: 'p')),
      );
      await tester.pumpAndSettle();
      expect(find.text('a.ply'), findsOneWidget);
      await tester.tap(find.byKey(const Key('upload-model')));
      await tester.pumpAndSettle();
      expect(controller.checkpoint!.model!.id, asset.id);
      expect(
        find.text(
          'Server acknowledged completion; check the refreshed model list',
        ),
        findsOneWidget,
      );
      await tester.ensureVisible(find.text('Finish local tracking'));
      await tester.tap(find.text('Finish local tracking'));
      await tester.pumpAndSettle();
      expect(controller.checkpoint, isNull);
      await tester.ensureVisible(find.text('Download to private cache'));
      await tester.tap(find.text('Download to private cache'));
      await tester.pumpAndSettle();
      expect(controller.cached(asset), isTrue);
      await tester.pumpWidget(app(CachePage(controller: controller)));
      await tester.pumpAndSettle();
      expect(find.text('Cached bytes: 10'), findsOneWidget);
      await tester.tap(find.text('Remove cached copy'));
      await tester.pumpAndSettle();
      expect(find.text('Cached bytes: 0'), findsOneWidget);
      expect(controller.cached(asset), isFalse);
      expect(repo.deletedModels, 0);
      await controller.download(asset);
      await controller.refreshCache();
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('clear-model-cache')));
      await tester.pumpAndSettle();
      expect(find.text('Bytes released: 10'), findsOneWidget);
      expect(find.text('Cached bytes: 0'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      sessions.dispose();
    },
  );
  testWidgets(
    'central model upload chooses a fixed owned project and account opens actual cache page',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final workspace = WorkspaceController(
        FakeProjects(),
        MemoryWorkspace(),
        sessions,
      );
      final files = CancelledPicker();
      final models = ModelAssetsController(
        DownloadRepo(),
        files,
        Journal(),
        WidgetCache(),
        sessions,
      );
      final container = AppContainer(
        sessions: sessions,
        auth: FakeAuth(),
        locales: MemoryLocale()..locale = 'en',
        closeTransport: () {},
        workspace: workspace,
        modelAssets: models,
      );
      await tester.pumpWidget(ProductApp(container: container));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('central-upload-model')));
      await tester.pumpAndSettle();
      expect(find.text('Fixed destination project'), findsOneWidget);
      await tester.tap(find.text('Project a'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.widgetWithText(FilledButton, 'Upload PLY/SPZ model'),
      );
      await tester.pumpAndSettle();
      expect(files.calls, 1);
      expect(models.checkpoint, isNull);
      await tester.tap(find.byKey(const Key('nav-account')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('account-cache')));
      await tester.tap(find.byKey(const Key('account-cache')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('clear-model-cache')), findsOneWidget);
      expect(controlledTarget('/account/cache'), '/account/cache');
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
  testWidgets(
    'model management wraps Phone Pad boundaries and 200 percent landscape text',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final controller = ModelAssetsController(
        DownloadRepo(),
        Files(),
        Journal(),
        WidgetCache(),
        sessions,
      );
      controller.synchronize();
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      for (final width in [360.0, 599.0, 600.0, 839.0, 840.0, 1280.0]) {
        tester.view.physicalSize = Size(width, 360);
        await tester.pumpWidget(
          app(
            MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(2)),
              child: ModelList(controller: controller, projectId: 'p'),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      }
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      sessions.dispose();
    },
  );
}
