import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../presentation/widgets/press_feedback.dart';
import '../../../shared/presentation/error_text.dart';
import '../domain/model_source.dart';
import '../domain/viewer_capabilities.dart';
import 'viewer_controller.dart';
import 'viewer_tools.dart';

class ViewerPage extends StatefulWidget {
  const ViewerPage({
    super.key,
    required this.controller,
    required this.surfaceBuilder,
    required this.onChooseCloud,
    this.libraryBuilder,
  });
  final ViewerController controller;
  final Widget Function() surfaceBuilder;
  final VoidCallback onChooseCloud;
  final WidgetBuilder? libraryBuilder;
  @override
  State<ViewerPage> createState() => _ViewerPageState();
}

class _ViewerPageState extends State<ViewerPage> {
  Widget? surface;
  @override
  void dispose() {
    widget.controller.surfaceRemoved();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller, s = c.session;
      final l = AppLocalizations.of(context);
      if (c.surfaceRequested && surface == null) {
        surface = widget.surfaceBuilder();
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(c.initialize());
        });
      }
      final busy = const [
        ViewerPhase.downloading,
        ViewerPhase.decoding,
        ViewerPhase.uploading,
        ViewerPhase.firstFrame,
      ].contains(s.phase);
      final preview =
          s.active != null ||
          const [
            ViewerPhase.decoding,
            ViewerPhase.uploading,
            ViewerPhase.firstFrame,
            ViewerPhase.recovering,
          ].contains(s.phase);
      final enabled = s.phase != ViewerPhase.unsupported;
      List<Widget> actions() => [
        IconButton(
          key: const Key('viewer-choose-cloud'),
          tooltip: l.viewerChooseCloud,
          onPressed: enabled ? widget.onChooseCloud : null,
          icon: const Icon(CupertinoIcons.cloud),
        ),
        IconButton(
          key: const Key('viewer-open-local'),
          tooltip: l.viewerOpenLocal,
          onPressed: enabled ? () => c.open(const LocalModelSource()) : null,
          icon: const Icon(CupertinoIcons.folder),
        ),
        IconButton(
          key: c.fullscreen ? const Key('exit-viewer-fullscreen') : null,
          tooltip: c.fullscreen ? l.viewerExitFullscreen : l.viewerFullscreen,
          onPressed: () => c.setFullscreen(!c.fullscreen),
          icon: Icon(
            c.fullscreen
                ? CupertinoIcons.fullscreen_exit
                : CupertinoIcons.fullscreen,
          ),
        ),
        IconButton(
          tooltip: l.viewerClose,
          onPressed: c.close,
          icon: const Icon(CupertinoIcons.xmark),
        ),
      ].map((button) => PressFeedback(child: button)).toList();
      final status = s.error != null
          ? errorText(l, s.error!)
          : switch (s.phase) {
              ViewerPhase.downloading => l.viewerDownload,
              ViewerPhase.decoding => l.viewerDecode,
              ViewerPhase.uploading || ViewerPhase.firstFrame => l.viewerUpload,
              ViewerPhase.unsupported => l.viewerUnsupported,
              ViewerPhase.recovering => l.viewerRecover,
              ViewerPhase.closing => l.viewerClosing,
              _ => null,
            };
      return PopScope(
        canPop: !c.fullscreen,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) c.setFullscreen(false);
        },
        child: Scaffold(
          appBar: c.fullscreen
              ? null
              : AppBar(
                  title: Text(
                    s.active?.fileName ?? l.viewer,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  actions: preview ? actions() : null,
                ),
          body: SafeArea(
            child: Stack(
              children: [
                // Native view identity remains stable after the first request.
                // Empty surfaces never paint through the opaque model library.
                Positioned.fill(
                  child: Offstage(
                    offstage: !preview,
                    child: surface ?? const SizedBox.shrink(),
                  ),
                ),
                if (!preview)
                  Positioned.fill(
                    child: Material(
                      color: Theme.of(context).scaffoldBackgroundColor,
                      child: Column(
                        children: [
                          if (status != null)
                            _LoadStatus(
                              text: status,
                              busy: busy,
                              onCancel: busy || s.phase == ViewerPhase.failed
                                  ? c.cancel
                                  : null,
                            ),
                          Expanded(
                            child: LayoutBuilder(
                              builder: (context, constraints) {
                                final library =
                                    widget.libraryBuilder?.call(context) ??
                                    Center(
                                      child: IconButton(
                                        tooltip: l.viewerChooseCloud,
                                        onPressed: enabled
                                            ? widget.onChooseCloud
                                            : null,
                                        icon: const Icon(CupertinoIcons.cloud),
                                      ),
                                    );
                                final local = _LocalEntry(
                                  enabled: enabled,
                                  onOpen: () =>
                                      c.open(const LocalModelSource()),
                                );
                                if (constraints.maxWidth < 600) {
                                  return Row(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Expanded(child: library),
                                      Padding(
                                        padding: const EdgeInsets.fromLTRB(
                                          0,
                                          16,
                                          16,
                                          0,
                                        ),
                                        child: PressFeedback(
                                          enabled: enabled,
                                          child: IconButton.filledTonal(
                                            key: const Key('viewer-open-local'),
                                            tooltip: l.viewerOpenLocal,
                                            onPressed: enabled
                                                ? () => c.open(
                                                    const LocalModelSource(),
                                                  )
                                                : null,
                                            icon: const Icon(
                                              CupertinoIcons.folder_badge_plus,
                                            ),
                                          ),
                                        ),
                                      ),
                                    ],
                                  );
                                }
                                return Row(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.stretch,
                                  children: [
                                    Expanded(flex: 3, child: library),
                                    const VerticalDivider(width: 1),
                                    Expanded(
                                      flex: 2,
                                      child: Center(child: local),
                                    ),
                                  ],
                                );
                              },
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                // SDK Empty does not guarantee a presented frame. Cover its whole
                // area until the first real model is ready.
                if (preview && s.active == null)
                  Positioned.fill(
                    child: ColoredBox(
                      color: Theme.of(context).scaffoldBackgroundColor,
                      child: Center(
                        child: _LoadStatus(
                          text: status ?? l.loading,
                          busy: busy,
                          onCancel: c.cancel,
                        ),
                      ),
                    ),
                  ),
                if (preview)
                  Theme(
                    data: Theme.of(context).copyWith(
                      colorScheme: const ColorScheme.dark(
                        primary: Color(0xffa8cee6),
                        surface: Color(0xff20262d),
                        onSurface: Colors.white,
                      ),
                      iconTheme: const IconThemeData(color: Colors.white),
                    ),
                    child: LayoutBuilder(
                      builder: (context, constraints) => Stack(
                        children: [
                          if (c.fullscreen)
                            Align(
                              alignment: Alignment.topRight,
                              child: Padding(
                                padding: const EdgeInsets.all(12),
                                child: Material(
                                  color: const Color(0xf220262d),
                                  borderRadius: BorderRadius.circular(18),
                                  child: Wrap(children: actions()),
                                ),
                              ),
                            ),
                          if (s.active != null && status != null)
                            Align(
                              alignment: Alignment.topLeft,
                              child: ConstrainedBox(
                                constraints: BoxConstraints(
                                  maxWidth: constraints.maxWidth * .65,
                                ),
                                child: _LoadStatus(
                                  text: status,
                                  busy: busy,
                                  onCancel:
                                      busy || s.phase == ViewerPhase.failed
                                      ? c.cancel
                                      : null,
                                ),
                              ),
                            ),
                          if (s.active != null)
                            Align(
                              alignment: Alignment.bottomCenter,
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Material(
                                  color: const Color(0xf220262d),
                                  borderRadius: BorderRadius.circular(20),
                                  child: constraints.maxWidth >= 600
                                      ? ConstrainedBox(
                                          constraints: BoxConstraints(
                                            maxHeight:
                                                constraints.maxHeight * .4,
                                          ),
                                          child: SingleChildScrollView(
                                            padding: const EdgeInsets.all(8),
                                            child: ViewerTools(controller: c),
                                          ),
                                        )
                                      : IconButton(
                                          key: const Key('viewer-tools'),
                                          tooltip: l.viewerTools,
                                          onPressed: () => _showTools(context),
                                          icon: const Icon(
                                            CupertinoIcons.slider_horizontal_3,
                                          ),
                                        ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      );
    },
  );
  void _showTools(BuildContext context) => showModalBottomSheet<void>(
    context: context,
    useSafeArea: true,
    isScrollControlled: true,
    showDragHandle: true,
    sheetAnimationStyle: MediaQuery.disableAnimationsOf(context)
        ? AnimationStyle.noAnimation
        : const AnimationStyle(
            duration: Duration(milliseconds: 220),
            reverseDuration: Duration(milliseconds: 180),
          ),
    builder: (context) => ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * .75,
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: ListenableBuilder(
          listenable: widget.controller,
          builder: (context, _) => ViewerTools(controller: widget.controller),
        ),
      ),
    ),
  );
}

class _LocalEntry extends StatelessWidget {
  const _LocalEntry({required this.enabled, required this.onOpen});
  final bool enabled;
  final VoidCallback onOpen;
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          PressFeedback(
            enabled: enabled,
            child: IconButton.filledTonal(
              key: const Key('viewer-open-local'),
              tooltip: l.viewerOpenLocal,
              iconSize: 30,
              padding: const EdgeInsets.all(20),
              onPressed: enabled ? onOpen : null,
              icon: const Icon(CupertinoIcons.folder_badge_plus),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            l.viewerLocalTitle,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          Text('PLY · SPZ', style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}

class _LoadStatus extends StatelessWidget {
  const _LoadStatus({required this.text, required this.busy, this.onCancel});
  final String text;
  final bool busy;
  final VoidCallback? onCancel;
  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.surfaceContainerHigh,
    borderRadius: BorderRadius.circular(16),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (busy) ...[
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            const SizedBox(width: 12),
          ],
          Flexible(
            child: Semantics(
              liveRegion: true,
              child: Text(
                text,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurface,
                ),
              ),
            ),
          ),
          if (onCancel != null)
            IconButton(
              tooltip: AppLocalizations.of(context).viewerCancel,
              onPressed: onCancel,
              icon: const Icon(CupertinoIcons.xmark_circle),
            ),
        ],
      ),
    ),
  );
}
