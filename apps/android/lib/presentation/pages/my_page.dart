import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

import '../../features/identity/domain/user_session.dart';
import '../../shared/l10n/app_localizations.dart';
import '../widgets/app_button.dart';
import '../widgets/app_sheet.dart';
import '../theme/app_tokens.dart';

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
    final tokens = Theme.of(context).extension<AppTokens>()!;
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
                  const SizedBox(height: 16),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: ExcludeSemantics(
                      child: CircleAvatar(
                        radius: 16 + MediaQuery.textScalerOf(context).scale(16),
                        backgroundColor: tokens.accentSoft,
                        foregroundColor: tokens.accent,
                        child: Text(
                          session.displayName.isEmpty
                              ? '?'
                              : session.displayName.characters.first,
                          style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
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
                  const SizedBox(height: 32),
                  Material(
                    color: tokens.card,
                    borderRadius: BorderRadius.circular(16),
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            l.userId,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          const SizedBox(height: 4),
                          SelectableText(session.userId),
                          const Divider(height: 32),
                          Text(
                            l.sessionExpiry,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            MaterialLocalizations.of(context)
                                .formatFullDate(session.expiresAt.toLocal()),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  Material(
                    color: tokens.card,
                    borderRadius: BorderRadius.circular(16),
                    clipBehavior: Clip.antiAlias,
                    child: Column(
                      children: [
                        if (onCache != null)
                          ListTile(
                            key: const Key('account-cache'),
                            onTap: onCache,
                            leading: const Icon(CupertinoIcons.tray),
                            trailing: const Icon(
                              CupertinoIcons.chevron_right,
                              size: 16,
                            ),
                            title: Text(l.cacheTitle),
                          ),
                        if (onCache != null)
                          const Divider(height: 1, indent: 60),
                        ListTile(
                          key: const Key('account-language'),
                          onTap: onToggleLocale,
                          leading: const Icon(CupertinoIcons.globe),
                          trailing: const Icon(
                            CupertinoIcons.chevron_right,
                            size: 16,
                          ),
                          title: Text(l.language),
                          subtitle: Text(l.toggleLanguage),
                        ),
                        const Divider(height: 1, indent: 60),
                        ListTile(
                          key: const Key('account-help'),
                          onTap: () => showAppSheet<void>(
                            context: context,
                            builder: (sheetContext) => Column(
                              mainAxisSize: MainAxisSize.min,
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Text(
                                  l.help,
                                  style: Theme.of(context)
                                      .textTheme
                                      .titleMedium,
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
                                  onPressed: () =>
                                      Navigator.of(sheetContext).pop(),
                                  child: Text(l.close),
                                ),
                              ],
                            ),
                          ),
                          leading: const Icon(CupertinoIcons.question_circle),
                          trailing: const Icon(
                            CupertinoIcons.chevron_right,
                            size: 16,
                          ),
                          title: Text(l.help),
                        ),
                      ],
                    ),
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
