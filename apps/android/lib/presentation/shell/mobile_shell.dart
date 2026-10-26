import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';

import '../../shared/l10n/app_localizations.dart';
import '../theme/app_tokens.dart';
import '../widgets/press_feedback.dart';

class MobileShell extends StatelessWidget {
  const MobileShell({
    super.key,
    required this.navigationShell,
    required this.onUpload,
    this.hideNavigation = false,
    this.onBeforeSwitch,
  });

  final StatefulNavigationShell navigationShell;
  final VoidCallback onUpload;
  final bool hideNavigation;
  final VoidCallback? onBeforeSwitch;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final labels = [l10n.home, l10n.projects, l10n.viewer, l10n.account];
    const icons = [
      CupertinoIcons.house,
      CupertinoIcons.folder,
      CupertinoIcons.cube,
      CupertinoIcons.person_crop_circle,
    ];
    Widget destination(int index) {
      final selected = navigationShell.currentIndex == index;
      return Expanded(
        child: Semantics(
          selected: selected,
          button: true,
          child: PressFeedback(
            child: InkWell(
              key: Key(
                'nav-${['home', 'projects', 'viewer', 'account'][index]}',
              ),
              onTap: () {
                onBeforeSwitch?.call();
                navigationShell.goBranch(index);
              },
              borderRadius: BorderRadius.circular(AppTokens.controlRadius),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 64, minWidth: 48),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 2,
                    vertical: 8,
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 18,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: selected
                              ? tokens.accentSoft
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(
                          icons[index],
                          color: selected
                              ? tokens.accent
                              : tokens.textSecondary,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        labels[index],
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: selected
                              ? FontWeight.w600
                              : FontWeight.w400,
                          color: selected
                              ? tokens.accent
                              : tokens.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    }

    return PopScope(
      canPop: navigationShell.currentIndex == 0 || hideNavigation,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && navigationShell.currentIndex != 0 && !hideNavigation) {
          navigationShell.goBranch(0);
        }
      },
      child: Scaffold(
        body: navigationShell,
        bottomNavigationBar: hideNavigation
            ? null
            : SafeArea(
                top: false,
                child: Align(
                  heightFactor: 1,
                  alignment: Alignment.bottomCenter,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 720),
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(8, 12, 8, 8),
                      child: Material(
                        color: tokens.card,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                          side: BorderSide(color: tokens.divider),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            destination(0),
                            destination(1),
                            Transform.translate(
                              offset: const Offset(0, -12),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 6,
                                ),
                                child: SizedBox.square(
                                  dimension: 60,
                                  child: PressFeedback(
                                    child: FloatingActionButton(
                                      key: const Key('central-upload-model'),
                                      heroTag: null,
                                      tooltip: l10n.upload,
                                      onPressed: onUpload,
                                      backgroundColor: tokens.accent,
                                      foregroundColor: Colors.white,
                                      elevation: 0,
                                      shape: const CircleBorder(),
                                      child: const Icon(
                                        CupertinoIcons.arrow_up,
                                        size: 26,
                                      ),
                                    ),
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
              ),
      ),
    );
  }
}
