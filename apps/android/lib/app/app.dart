import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../presentation/theme/app_theme.dart';
import '../shared/l10n/app_localizations.dart';

/// Product app host. The composition root owns and disposes [router].
/// P0 has a separate developer entry; only working capabilities are composed.
class XjiCloudApp extends StatelessWidget {
  const XjiCloudApp({
    super.key,
    required this.router,
    this.locale,
    this.builder,
  });

  final GoRouter router;
  final Locale? locale;
  final TransitionBuilder? builder;

  @override
  Widget build(BuildContext context) => MaterialApp.router(
    routerConfig: router,
    debugShowCheckedModeBanner: false,
    theme: AppTheme.light,
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
    builder: builder,
  );
}
