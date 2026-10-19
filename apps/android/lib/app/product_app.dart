import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/identity/presentation/auth_controller.dart';
import '../features/identity/presentation/login_page.dart';
import '../features/project/presentation/home_page.dart';
import '../features/project/presentation/project_list_page.dart';
import '../features/project/presentation/project_detail_page.dart';
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
      target == '/home' ||
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
          ShellRoute(
            builder: (context, state, child) =>
                _WorkspaceNavigation(location: state.uri.path, child: child),
            routes: [
              GoRoute(
                path: '/home',
                builder: (context, state) {
                  final workspace = container.workspace!;
                  return ListenableBuilder(
                    listenable: workspace,
                    builder: (context, _) => HomePage(
                      viewModel: workspace.home,
                      onCreate: () => showProjectEditor(context, workspace),
                      onRetry: workspace.refresh,
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
                builder: (context, state) =>
                    ProjectListPage(controller: container.workspace!),
                routes: [
                  GoRoute(
                    path: ':id',
                    builder: (context, state) => ProjectListPage(
                      controller: container.workspace!,
                      projectId: state.pathParameters['id'],
                    ),
                  ),
                ],
              ),
              GoRoute(path: '/account', builder: accountPage),
            ],
          ),
        if (container.workspace == null)
          GoRoute(path: '/account', builder: accountPage),
      ],
    );
    initialize();
  }

  Widget accountPage(BuildContext context, GoRouterState state) {
    final session = container.sessions.session;
    return session == null
        ? const SizedBox.shrink()
        : MyPage(
            session: session,
            onToggleLocale: toggleLocale,
            onLogout: () => container.sessions.invalidate(),
          );
  }

  Future<void> initialize() async {
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
    if (state == AppLifecycleState.resumed) container.sessions.checkExpiry();
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
          if (localeError != null)
            Material(
              child: SafeArea(
                bottom: false,
                child: Text(
                  errorText(AppLocalizations.of(context), localeError!),
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
  const _WorkspaceNavigation({required this.location, required this.child});
  final String location;
  final Widget child;
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
