import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../presentation/shell/mobile_shell.dart';

/// Each builder is supplied only when its real capability is available.
/// This file registers no placeholder destination or upload content.
final class ShellDestinations {
  const ShellDestinations({
    required this.home,
    required this.projects,
    required this.viewer,
    required this.account,
  });

  final GoRouterWidgetBuilder home;
  final GoRouterWidgetBuilder projects;
  final GoRouterWidgetBuilder viewer;
  final GoRouterWidgetBuilder account;
}

GoRouter createAppRouter({
  required List<RouteBase> routes,
  required String initialLocation,
  GoRouterRedirect? redirect,
  Listenable? refreshListenable,
}) => GoRouter(
  routes: routes,
  initialLocation: initialLocation,
  redirect: redirect,
  refreshListenable: refreshListenable,
);

/// Upload is an independent action: its owner can show a chooser, push a flow,
/// or enforce a draft/transfer guard. It never changes the branch index.
StatefulShellRoute createMobileShellRoute({
  required ShellDestinations destinations,
  required void Function(BuildContext context) onUpload,
}) => StatefulShellRoute.indexedStack(
  builder: (context, state, shell) =>
      MobileShell(navigationShell: shell, onUpload: () => onUpload(context)),
  branches: [
    for (final entry in <String, GoRouterWidgetBuilder>{
      '/home': destinations.home,
      '/projects': destinations.projects,
      '/viewer': destinations.viewer,
      '/account': destinations.account,
    }.entries)
      StatefulShellBranch(
        routes: [GoRoute(path: entry.key, builder: entry.value)],
      ),
  ],
);
