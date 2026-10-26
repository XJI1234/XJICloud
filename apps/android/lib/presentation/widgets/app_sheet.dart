import 'package:flutter/material.dart';

import '../../shared/l10n/app_localizations.dart';
import '../theme/app_tokens.dart';

enum UploadChoice { images, model }

/// Only chooses a capability. Project selection, file picking and transfer
/// lifecycles belong to the real upload flow supplied by the composition root.
Future<UploadChoice?> showUploadChooser({required BuildContext context}) =>
    showAppSheet<UploadChoice>(
      context: context,
      builder: (context) {
        final l10n = AppLocalizations.of(context);
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final choice in UploadChoice.values)
              ListTile(
                minTileHeight: 56,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 8,
                  vertical: 8,
                ),
                leading: Icon(
                  choice == UploadChoice.images
                      ? Icons.photo_library_outlined
                      : Icons.view_in_ar,
                ),
                title: Text(
                  choice == UploadChoice.images
                      ? l10n.uploadImages
                      : l10n.uploadModel,
                ),
                onTap: () => Navigator.of(context).pop(choice),
              ),
          ],
        );
      },
    );

/// Scrollable keyboard-safe forms: bottom sheet on compact windows, dialog
/// above 840dp. The caller owns form state and any unsaved-draft pop guard.
/// Both modes cover the root navigator, including retained branch tab chrome.
Future<T?> showAppSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
}) {
  final reduceMotion = MediaQuery.disableAnimationsOf(context);
  final wide = MediaQuery.sizeOf(context).width >= 840;
  if (wide) {
    return showDialog<T>(
      context: context,
      animationStyle: reduceMotion
          ? AnimationStyle.noAnimation
          : const AnimationStyle(duration: AppTokens.sheetDuration),
      builder: (context) => Dialog(
        constraints: const BoxConstraints(maxWidth: 480),
        child: _SheetContent(builder: builder),
      ),
    );
  }
  return showModalBottomSheet<T>(
    context: context,
    useRootNavigator: true,
    isScrollControlled: true,
    useSafeArea: true,
    sheetAnimationStyle: reduceMotion
        ? AnimationStyle.noAnimation
        : const AnimationStyle(duration: AppTokens.sheetDuration),
    builder: (context) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: _SheetContent(builder: builder),
    ),
  );
}

class _SheetContent extends StatelessWidget {
  const _SheetContent({required this.builder});
  final WidgetBuilder builder;

  @override
  Widget build(BuildContext context) => SafeArea(
    top: false,
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Builder(builder: builder),
    ),
  );
}
