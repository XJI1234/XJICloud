import 'package:flutter/material.dart';

import '../../shared/l10n/app_localizations.dart';
import '../theme/app_tokens.dart';
import 'press_feedback.dart';

enum AppButtonVariant { primary, ghost, destructive }

class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.variant = AppButtonVariant.primary,
    this.isLoading = false,
    this.loadingLabel,
  });

  final String label;
  final VoidCallback? onPressed;
  final AppButtonVariant variant;
  final bool isLoading;
  final String? loadingLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final ghost = variant == AppButtonVariant.ghost;
    final foreground = ghost ? tokens.accent : Colors.white;
    final background = variant == AppButtonVariant.destructive
        ? tokens.error
        : tokens.accent;
    final disabled = isLoading || onPressed == null;
    final displayedLabel = isLoading
        ? loadingLabel ?? AppLocalizations.of(context).loading
        : label;
    return PressFeedback(
      enabled: !disabled,
      child: Semantics(
        liveRegion: isLoading,
        child: TextButton(
          onPressed: disabled ? null : onPressed,
          style: ButtonStyle(
            minimumSize: const WidgetStatePropertyAll(Size(48, 48)),
            padding: const WidgetStatePropertyAll(
              EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            ),
            shape: WidgetStatePropertyAll(
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            ),
            foregroundColor: WidgetStatePropertyAll(
              disabled ? tokens.textSecondary : foreground,
            ),
            backgroundColor: WidgetStateProperty.resolveWith(
              (states) => disabled
                  ? tokens.divider
                  : ghost
                  ? Colors.transparent
                  : states.contains(WidgetState.pressed) &&
                        variant == AppButtonVariant.primary
                  ? tokens.accentPressed
                  : background,
            ),
          ),
          child: Text(displayedLabel, textAlign: TextAlign.center),
        ),
      ),
    );
  }
}
