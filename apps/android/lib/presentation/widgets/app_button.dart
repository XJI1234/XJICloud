import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../../shared/l10n/app_localizations.dart';
import '../theme/app_tokens.dart';

enum AppButtonVariant { primary, ghost, destructive }

class AppButton extends StatefulWidget {
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
  State<AppButton> createState() => _AppButtonState();
}

class _AppButtonState extends State<AppButton> {
  final states = WidgetStatesController();
  bool pressed = false;

  @override
  void initState() {
    super.initState();
    states.addListener(_statesChanged);
  }

  void _statesChanged() {
    final next = states.value.contains(WidgetState.pressed);
    if (next == pressed) return;
    pressed = next;
    // A parent may disable a held button while rebuilding. Repaint after that
    // frame rather than mark an ancestor dirty from the button's build.
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) setState(() {});
      });
    } else {
      setState(() {});
    }
  }

  @override
  void dispose() {
    states.removeListener(_statesChanged);
    states.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<AppTokens>()!;
    final ghost = widget.variant == AppButtonVariant.ghost;
    final foreground = ghost ? tokens.accent : Colors.white;
    final background = widget.variant == AppButtonVariant.destructive
        ? tokens.error
        : tokens.accent;
    final disabled = widget.isLoading || widget.onPressed == null;
    final label = widget.isLoading
        ? widget.loadingLabel ?? AppLocalizations.of(context).loading
        : widget.label;
    return AnimatedScale(
      scale: pressed && !MediaQuery.disableAnimationsOf(context) ? .97 : 1,
      duration: MediaQuery.disableAnimationsOf(context)
          ? Duration.zero
          : AppTokens.pressDuration,
      child: Semantics(
        liveRegion: widget.isLoading,
        child: TextButton(
          onPressed: disabled ? null : widget.onPressed,
          statesController: states,
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
                        widget.variant == AppButtonVariant.primary
                  ? tokens.accentPressed
                  : background,
            ),
          ),
          child: Text(label, textAlign: TextAlign.center),
        ),
      ),
    );
  }
}
