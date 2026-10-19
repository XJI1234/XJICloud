import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../shared/l10n/app_localizations.dart';
import '../theme/app_tokens.dart';

class MobileShell extends StatelessWidget {
  const MobileShell({
    super.key,
    required this.navigationShell,
    required this.onUpload,
  });

  final StatefulNavigationShell navigationShell;
  final VoidCallback onUpload;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final labels = [l10n.home, l10n.projects, l10n.viewer, l10n.account];
    const icons = [
      Icons.home_outlined,
      Icons.folder_outlined,
      Icons.view_in_ar_outlined,
      Icons.person_outline,
    ];
    Widget destination(int index) {
      final selected = navigationShell.currentIndex == index;
      return Expanded(
        child: Semantics(
          selected: selected,
          button: true,
          child: InkWell(
            onTap: () => navigationShell.goBranch(index),
            borderRadius: BorderRadius.circular(AppTokens.controlRadius),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 64, minWidth: 48),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 8),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      icons[index],
                      color: selected ? tokens.accent : tokens.textSecondary,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      labels[index],
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: selected ? tokens.accent : tokens.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }

    return PopScope(
      canPop: navigationShell.currentIndex == 0,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && navigationShell.currentIndex != 0) {
          navigationShell.goBranch(0);
        }
      },
      child: Scaffold(
        body: navigationShell,
        bottomNavigationBar: Material(
          color: tokens.card,
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  destination(0),
                  destination(1),
                  Transform.translate(
                    offset: const Offset(0, -12),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                      child: SizedBox.square(
                        dimension: 60,
                        child: FloatingActionButton(
                          heroTag: null,
                          tooltip: l10n.upload,
                          onPressed: onUpload,
                          backgroundColor: tokens.accent,
                          foregroundColor: Colors.white,
                          elevation: 0,
                          shape: const CircleBorder(),
                          child: const Icon(Icons.arrow_upward),
                        ),
                      ),
                    ),
                  ),
                  destination(2),
                  destination(3),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
