import 'package:flutter/material.dart';

import '../../features/identity/domain/user_session.dart';
import '../../shared/l10n/app_localizations.dart';
import '../widgets/app_button.dart';
import '../widgets/app_sheet.dart';

class MyPage extends StatelessWidget {
  const MyPage({
    super.key,
    required this.session,
    required this.onToggleLocale,
    required this.onLogout,
    this.onCache,
  });
  final UserSession session;
  final VoidCallback onToggleLocale;
  final VoidCallback onLogout;
  final VoidCallback? onCache;
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.account)),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    session.displayName,
                    key: const Key('account-display-name'),
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 12),
                  SelectableText(
                    session.username,
                    key: const Key('account-username'),
                  ),
                  const SizedBox(height: 16),
                  Text(l.userId),
                  SelectableText(session.userId),
                  const SizedBox(height: 16),
                  Text(l.sessionExpiry),
                  Text(
                    MaterialLocalizations.of(context)
                        .formatFullDate(session.expiresAt.toLocal()),
                  ),
                  const SizedBox(height: 32),
                  if (onCache != null)
                    TextButton(
                      key: const Key('account-cache'),
                      onPressed: onCache,
                      child: Text(l.cacheTitle),
                    ),
                  TextButton(
                    key: const Key('account-language'),
                    onPressed: onToggleLocale,
                    child: Text('${l.language} · ${l.toggleLanguage}'),
                  ),
                  TextButton(
                    key: const Key('account-help'),
                    onPressed: () => showAppSheet<void>(
                      context: context,
                      builder: (sheetContext) => Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            l.help,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 16),
                          Text(l.helpContent),
                          const SizedBox(height: 16),
                          Text(l.sdkAbout),
                          TextButton(
                            key: const Key('view-licenses'),
                            onPressed: () => showLicensePage(
                              context: sheetContext,
                              applicationName: l.appTitle,
                            ),
                            child: Text(l.viewLicenses),
                          ),
                          const SizedBox(height: 16),
                          TextButton(
                            key: const Key('help-close'),
                            onPressed: () => Navigator.of(sheetContext).pop(),
                            child: Text(l.close),
                          ),
                        ],
                      ),
                    ),
                    child: Text(l.help),
                  ),
                  const SizedBox(height: 24),
                  AppButton(
                    key: const Key('account-logout'),
                    label: l.logout,
                    onPressed: onLogout,
                    variant: AppButtonVariant.destructive,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
