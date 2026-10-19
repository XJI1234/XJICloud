import 'dart:async';

import '../presentation/shell/mobile_shell.dart';
import '../features/viewer/presentation/viewer_page.dart';
import '../features/viewer/domain/model_source.dart';
import '../features/model_asset/domain/model_asset.dart';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/identity/presentation/auth_controller.dart';
import '../features/identity/presentation/login_page.dart';
import '../features/project/presentation/home_page.dart';
import '../features/project/presentation/project_list_page.dart';
import '../features/project/presentation/project_detail_page.dart';
import '../features/model_asset/presentation/model_list.dart';
import '../features/model_asset/presentation/cache_page.dart';
import '../features/dataset_training/presentation/upload_flow_page.dart';
import '../features/dataset_training/presentation/training_job_panel.dart';
import '../presentation/pages/my_page.dart';
import '../presentation/theme/app_theme.dart';
import '../shared/l10n/app_localizations.dart';
import '../shared/domain/domain_error.dart';
import '../shared/domain/result.dart';
import '../shared/presentation/error_text.dart';
import 'app.dart';
import 'app_container.dart';
import 'router.dart';

/// A destination must exist before it can be retained through a login redirect.
String controlledTarget(String? target) {
  if (target == '/account' ||
      target == '/account/cache' ||
      target == '/home' ||
      target == '/viewer' ||
      target != null &&
          RegExp(
            r'^/viewer\?projectId=[0-9a-fA-F-]{36}&modelId=[0-9a-fA-F-]{36}$',
          ).hasMatch(target) ||
      target == '/projects' ||
      target != null &&
          RegExp(
            r'^/projects/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
          ).hasMatch(target)) {
    return target!;
  }
  return '/account';
}

class ProductApp extends StatefulWidget {
  const ProductApp({
    super.key,
    required this.container,
    this.initialLocation,
    this.disposeContainer = true,
  });
  final AppContainer container;
  final String? initialLocation;
  final bool disposeContainer;
  @override
  State<ProductApp> createState() => _ProductAppState();
}

class _ProductAppState extends State<ProductApp> with WidgetsBindingObserver {
  late final GoRouter router;
  Locale? locale;
  DomainError? localeError;
  DomainError? storageError;
  bool restoring = true;
  AppContainer get container => widget.container;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    router = createAppRouter(
      initialLocation:
          widget.initialLocation ??
          (container.workspace == null ? '/account' : '/home'),
      refreshListenable: container.sessions,
      redirect: (context, state) {
        final session = container.sessions.session;
        final authenticated =
            session != null && !session.isExpired(DateTime.now());
        if (!authenticated && state.uri.path != '/login') {
          return Uri(
            path: '/login',
            queryParameters: {'from': controlledTarget(state.uri.toString())},
          ).toString();
        }
        if (authenticated && state.uri.path == '/login') {
          final target = controlledTarget(state.uri.queryParameters['from']);
          return container.workspace == null && target != '/account'
              ? '/account'
              : target;
        }
        return null;
      },
      routes: [
        GoRoute(
          path: '/login',
          builder: (context, state) =>
              _AuthRoute(container: container, onToggleLocale: toggleLocale),
        ),
        if (container.workspace != null)
          workspaceShell([
            GoRoute(
              path: '/home',
              builder: (context, state) {
                final workspace = container.workspace!;
                return ListenableBuilder(
                  listenable: workspace,
                  builder: (context, _) => HomePage(
                    viewModel: workspace.home,
                    onCreate: () => showProjectEditor(context, workspace),
                    onOpenLocal: container.viewer == null
                        ? null
                        : () {
                            container.viewer!.open(const LocalModelSource());
                            context.go('/viewer');
                          },
                    onRetry: workspace.refresh,
                    trainingSectionBuilder: container.training == null
                        ? null
                        : (id) => TrainingJobPanel(
                            controller: container.training!,
                            projectId: id,
                            summary: true,
                            onUpload: () =>
                                showDatasetUpload(context, projectId: id),
                          ),
                    onOpen: (id) async {
                      final opened = await workspace.openProject(id);
                      if (opened && context.mounted) {
                        workspace.select(id);
                        context.go('/projects/$id');
                      }
                    },
                  ),
                );
              },
            ),
            GoRoute(
              path: '/projects',
              builder: (context, state) => ProjectListPage(
                controller: container.workspace!,
                modelSectionBuilder: modelSectionBuilder,
              ),
              routes: [
                GoRoute(
                  path: ':id',
                  builder: (context, state) => ProjectListPage(
                    controller: container.workspace!,
                    projectId: state.pathParameters['id'],
                    modelSectionBuilder: modelSectionBuilder,
                  ),
                ),
              ],
            ),
            GoRoute(
              path: '/account',
              builder: accountPage,
              routes: [
                if (container.modelAssets != null)
                  GoRoute(
                    path: 'cache',
                    builder: (context, state) =>
                        CachePage(controller: container.modelAssets!),
                  ),
              ],
            ),
          ]),
        if (container.workspace == null)
          GoRoute(path: '/account', builder: accountPage),
      ],
    );
    initialize();
  }

  String? lastViewerTarget;
  RouteBase workspaceShell(List<RouteBase> routes) {
    final viewer = container.viewer;
    if (viewer == null) {
      return ShellRoute(
        builder: (context, state, child) => _WorkspaceNavigation(
          location: state.uri.path,
          onUpload: container.modelAssets == null && container.training == null
              ? null
              : () => showUpload(context),
          child: child,
        ),
        routes: routes,
      );
    }
    return StatefulShellRoute.indexedStack(
      builder: (context, state, shell) {
        final visible = shell.currentIndex == 2;
        if (!visible) lastViewerTarget = null;
        if (viewer.visible != visible) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) unawaited(viewer.setVisible(visible));
          });
        }
        return ListenableBuilder(
          listenable: viewer,
          builder: (context, _) => MobileShell(
            navigationShell: shell,
            onUpload: () => showUpload(context),
            hideNavigation: visible && viewer.fullscreen,
            onBeforeSwitch: () => viewer.setFullscreen(false),
          ),
        );
      },
      branches: [
        StatefulShellBranch(routes: [routes[0]]),
        StatefulShellBranch(routes: [routes[1]]),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/viewer',
              builder: (context, state) {
                final project = state.uri.queryParameters['projectId'],
                    model = state.uri.queryParameters['modelId'];
                final target = '${container.sessions.generation}:${state.uri}';
                if (project != null &&
                    model != null &&
                    target != lastViewerTarget) {
                  lastViewerTarget = target;
                  WidgetsBinding.instance.addPostFrameCallback((_) async {
                    final generation = container.sessions.generation;
                    await container.modelAssets?.refresh(project);
                    if (!mounted ||
                        generation != container.sessions.generation ||
                        target != lastViewerTarget) {
                      return;
                    }
                    final models =
                        container.modelAssets?.modelsFor(project) ??
                        <ModelAsset>[];
                    final matches = models.where((asset) => asset.id == model);
                    if (matches.isNotEmpty) {
                      await viewer.open(CloudModelSource(matches.first));
                    } else {
                      viewer.reportError(
                        const DomainError('model_unavailable'),
                      );
                    }
                  });
                }
                return ViewerPage(
                  controller: viewer,
                  surfaceBuilder: container.viewerSurface!,
                  onChooseCloud: () => showViewerModels(context),
                );
              },
            ),
          ],
        ),
        StatefulShellBranch(routes: [routes[2]]),
      ],
    );
  }

  void openCloud(BuildContext context, ModelAsset model) {
    final uri = '/viewer?projectId=${model.projectId}&modelId=${model.id}';
    lastViewerTarget = '${container.sessions.generation}:$uri';
    unawaited(container.viewer!.open(CloudModelSource(model)));
    context.go(uri);
  }

  Future<void> showViewerModels(BuildContext context) async {
    final id = container.workspace?.activeProjectId,
        models = container.modelAssets;
    if (id == null || models == null) {
      context.go('/projects');
      return;
    }
    final generation = container.sessions.generation;
    unawaited(models.refresh(id));
    final selected = await sessionSheet<ModelAsset>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      builder: (context) => ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .75,
        ),
        child: ListenableBuilder(
          listenable: models,
          builder: (context, _) => SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(AppLocalizations.of(context).viewerChooseCloud),
                if (models.loading(id))
                  Text(AppLocalizations.of(context).loading),
                if (models.listError(id) case final error?)
                  Text(errorText(AppLocalizations.of(context), error)),
                if (!models.loading(id) && models.modelsFor(id).isEmpty)
                  Text(AppLocalizations.of(context).modelEmpty),
                for (final model in models.modelsFor(id))
                  ListTile(
                    title: Text(model.fileName),
                    subtitle: Text('${model.format} · ${model.sizeBytes} B'),
                    onTap: () => Navigator.pop(context, model),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    if (selected != null &&
        generation == container.sessions.generation &&
        context.mounted) {
      openCloud(context, selected);
    }
  }

  Widget accountPage(BuildContext context, GoRouterState state) {
    final session = container.sessions.session;
    return session == null
        ? const SizedBox.shrink()
        : MyPage(
            session: session,
            onToggleLocale: toggleLocale,
            onLogout: () => container.sessions.invalidate(),
            onCache: container.modelAssets == null
                ? null
                : () => context.go('/account/cache'),
          );
  }

  Future<void> initialize() async {
    final storage = await container.initializeStorage?.call();
    if (storage case Failure<void>(:final error)) storageError = error;
    final preferred = await container.locales.read();
    await container.sessions.restore();
    if (!mounted) return;
    setState(() {
      restoring = false;
      switch (preferred) {
        case Success<String?>(:final value):
          if (value == 'en' || value == 'zh') locale = Locale(value!);
        case Failure<String?>(:final error):
          localeError = error;
      }
    });
  }

  Widget Function(String)? get modelSectionBuilder =>
      container.modelAssets == null && container.training == null
      ? null
      : (id) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (container.modelAssets != null)
              Builder(
                builder: (context) => ModelList(
                  controller: container.modelAssets!,
                  projectId: id,
                  onView: container.viewer == null
                      ? null
                      : (model) => openCloud(context, model),
                ),
              ),
            if (container.training != null)
              Builder(
                builder: (context) => TrainingJobPanel(
                  controller: container.training!,
                  projectId: id,
                  onViewOutput: container.viewer == null
                      ? null
                      : (output) {
                          container.viewer!.open(TrainingModelSource(output));
                          context.go('/viewer');
                        },
                  onUpload: () => showDatasetUpload(context, projectId: id),
                ),
              ),
          ],
        );

  Future<void> showUpload(BuildContext context) async {
    if (container.training == null) {
      await showModelUpload(context);
      return;
    }
    final l = AppLocalizations.of(context), g = container.sessions.generation;
    final kind = await sessionSheet<String>(
      context: context,
      useSafeArea: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FilledButton.icon(
              key: const Key('upload-dataset-choice'),
              onPressed: () => Navigator.pop(context, 'images'),
              icon: const Icon(Icons.photo_library_outlined),
              label: Text(l.uploadImages),
            ),
            if (container.modelAssets != null)
              OutlinedButton.icon(
                onPressed: () => Navigator.pop(context, 'model'),
                icon: const Icon(Icons.view_in_ar_outlined),
                label: Text(l.uploadModel),
              ),
          ],
        ),
      ),
    );
    if (!context.mounted || g != container.sessions.generation) return;
    if (kind == 'images') {
      await showDatasetUpload(context);
    } else if (kind == 'model') {
      await showModelUpload(context);
    }
  }

  Future<void> showDatasetUpload(
    BuildContext context, {
    String? projectId,
  }) async {
    final c = container.training, workspace = container.workspace;
    if (c == null || workspace == null) return;
    final g = c.userGeneration, l = AppLocalizations.of(context);
    String? selected =
        c.targetProjectId ?? projectId ?? workspace.activeProjectId;
    if (c.targetProjectId == null) {
      selected = await sessionSheet<String>(
        context: context,
        useSafeArea: true,
        isScrollControlled: true,
        builder: (context) => ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * .8,
          ),
          child: StatefulBuilder(
            builder: (context, setState) => SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    l.uploadDestination,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  if (workspace.projects.isEmpty) ...[
                    Text(l.noCurrentProject),
                    TextButton(
                      onPressed: () {
                        Navigator.pop(context);
                        showProjectEditor(this.context, workspace);
                      },
                      child: Text(l.createProject),
                    ),
                  ],
                  for (final project in workspace.projects)
                    ListTile(
                      selected: selected == project.id,
                      leading: Icon(
                        selected == project.id
                            ? Icons.radio_button_checked
                            : Icons.radio_button_off,
                      ),
                      onTap: () => setState(() => selected = project.id),
                      title: Text(project.name),
                    ),
                  FilledButton(
                    onPressed: selected == null
                        ? null
                        : () => Navigator.pop(context, selected),
                    child: Text(l.uploadImages),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    }
    if (selected == null || g != c.userGeneration || !context.mounted) return;
    final fixedId = selected!;
    final project = workspace.projectById(fixedId);
    if (project == null && c.targetProjectId != fixedId) return;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => UploadFlowPage(
          controller: c,
          projectId: fixedId,
          projectName: project?.name ?? fixedId,
        ),
      ),
    );
  }

  Future<T?> sessionSheet<T>({
    required BuildContext context,
    required WidgetBuilder builder,
    bool useSafeArea = true,
    bool isScrollControlled = false,
  }) async {
    final generation = container.sessions.generation;
    BuildContext? sheet;
    void changed() {
      if (container.sessions.generation != generation &&
          sheet?.mounted == true) {
        Navigator.of(sheet!).pop();
      }
    }

    container.sessions.addListener(changed);
    try {
      return await showModalBottomSheet<T>(
        context: context,
        useSafeArea: useSafeArea,
        isScrollControlled: isScrollControlled,
        builder: (context) {
          sheet = context;
          return builder(context);
        },
      );
    } finally {
      container.sessions.removeListener(changed);
    }
  }

  Future<void> showModelUpload(BuildContext context) async {
    final models = container.modelAssets!, workspace = container.workspace!;
    final generation = models.userGeneration;
    final l = AppLocalizations.of(context);
    final hostContext = context;
    var selected = workspace.activeProjectId;
    BuildContext? sheetContext;
    void identityChanged() {
      if (generation != models.userGeneration &&
          sheetContext?.mounted == true) {
        Navigator.of(sheetContext!).pop();
      }
    }

    models.addListener(identityChanged);
    String? chosen;
    try {
      chosen = await showModalBottomSheet<String>(
        context: context,
        useSafeArea: true,
        isScrollControlled: true,
        builder: (context) {
          sheetContext = context;
          return ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * .8,
            ),
            child: ListenableBuilder(
              listenable: models,
              builder: (context, _) => StatefulBuilder(
                builder: (context, setState) => SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        l.uploadModel,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      Text(l.modelUploadPolicy),
                      if (models.error case final error?)
                        Text(errorText(l, error)),
                      if (models.checkpoint != null || models.uploadBusy)
                        UploadStatus(controller: models)
                      else if (workspace.projects.isEmpty) ...[
                        Text(l.noCurrentProject),
                        TextButton(
                          onPressed: () {
                            Navigator.pop(context);
                            if (generation == models.userGeneration &&
                                hostContext.mounted) {
                              showProjectEditor(hostContext, workspace);
                            }
                          },
                          child: Text(l.createProject),
                        ),
                      ] else ...[
                        const SizedBox(height: 16),
                        Text(l.uploadDestination),
                        for (final project in workspace.projects)
                          ListTile(
                            selected: selected == project.id,
                            leading: Icon(
                              selected == project.id
                                  ? Icons.radio_button_checked
                                  : Icons.radio_button_off,
                            ),
                            title: Text(project.name),
                            onTap: () => setState(() => selected = project.id),
                          ),
                        FilledButton(
                          onPressed: selected == null || models.restoring
                              ? null
                              : () => Navigator.pop(context, selected),
                          child: Text(l.uploadModel),
                        ),
                      ],
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: Text(l.close),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      );
    } finally {
      models.removeListener(identityChanged);
    }
    if (chosen != null && generation == models.userGeneration && mounted) {
      await models.chooseAndUpload(chosen);
      if (hostContext.mounted &&
          generation == models.userGeneration &&
          models.error != null) {
        ScaffoldMessenger.of(hostContext)
            .showSnackBar(SnackBar(content: Text(errorText(l, models.error!))));
      }
    }
  }

  Future<void> toggleLocale() async {
    final current =
        locale?.languageCode ??
        View.of(context).platformDispatcher.locale.languageCode;
    final next = current == 'zh' ? 'en' : 'zh';
    final saved = await container.locales.write(next);
    if (!mounted) return;
    setState(() {
      switch (saved) {
        case Success<void>():
          locale = Locale(next);
          localeError = null;
        case Failure<void>(:final error):
          localeError = error;
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      container.sessions.checkExpiry();
      container.modelAssets?.foreground();
      container.training?.foreground();
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden) {
      container.modelAssets?.background();
      container.training?.background();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    router.dispose();
    if (widget.disposeContainer) container.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => XjiCloudApp(
    router: router,
    locale: locale,
    builder: (context, child) {
      if (restoring) {
        return Scaffold(
          body: SafeArea(
            child: Center(child: Text(AppLocalizations.of(context).loading)),
          ),
        );
      }
      return Column(
        children: [
          if (storageError != null || localeError != null)
            Material(
              child: SafeArea(
                bottom: false,
                child: Text(
                  errorText(
                    AppLocalizations.of(context),
                    storageError ?? localeError!,
                  ),
                ),
              ),
            ),
          Expanded(child: child!),
        ],
      );
    },
  );
}

/// Only released capabilities are navigable; Task 7 assembles the full shell.
class _WorkspaceNavigation extends StatelessWidget {
  const _WorkspaceNavigation({
    required this.location,
    required this.child,
    this.onUpload,
  });
  final String location;
  final Widget child;
  final VoidCallback? onUpload;
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final paths = ['/home', '/projects', '/account'];
    final labels = [l.home, l.projects, l.account];
    const icons = [
      Icons.home_outlined,
      Icons.folder_outlined,
      Icons.person_outline,
    ];
    return Scaffold(
      body: child,
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: onUpload == null
          ? null
          : FloatingActionButton(
              key: const Key('central-upload-model'),
              tooltip: l.uploadModel,
              onPressed: onUpload,
              child: const Icon(Icons.upload),
            ),
      bottomNavigationBar: SafeArea(
        top: false,
        child: Material(
          color: Colors.white,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (var i = 0; i < paths.length; i++)
                  Expanded(
                    child: Semantics(
                      selected: location.startsWith(paths[i]),
                      child: TextButton(
                        key: Key('nav-${paths[i].substring(1)}'),
                        onPressed: () => context.go(paths[i]),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(icons[i]),
                            const SizedBox(height: 4),
                            Text(labels[i], textAlign: TextAlign.center),
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AuthRoute extends StatefulWidget {
  const _AuthRoute({required this.container, required this.onToggleLocale});
  final AppContainer container;
  final VoidCallback onToggleLocale;
  @override
  State<_AuthRoute> createState() => _AuthRouteState();
}

class _AuthRouteState extends State<_AuthRoute> {
  late final AuthController controller;
  @override
  void initState() {
    super.initState();
    controller = widget.container.createAuthController();
    widget.container.sessions.addListener(changed);
  }

  void changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    widget.container.sessions.removeListener(changed);
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LoginPage(
    controller: controller,
    onToggleLocale: widget.onToggleLocale,
    sessionError: widget.container.sessions.error,
    onRetrySession: () => widget.container.sessions.invalidate(),
  );
}

/// Misconfigured builds fail visibly before constructing any network adapter.
class ConfigurationErrorApp extends StatelessWidget {
  const ConfigurationErrorApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    theme: AppTheme.light,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Builder(
      builder: (context) => Scaffold(
        body: SafeArea(
          child: Center(
            child: Text(AppLocalizations.of(context).configurationError),
          ),
        ),
      ),
    ),
  );
}
