import 'dart:async';

import 'package:flutter/material.dart';

import '../../../shared/l10n/app_localizations.dart';
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
  });
  final ViewerController controller;
  final Widget Function() surfaceBuilder;
  final VoidCallback onChooseCloud;
  @override
  State<ViewerPage> createState() => _ViewerPageState();
}

class _ViewerPageState extends State<ViewerPage> {
  late final Widget surface;
  @override
  void initState() {
    super.initState();
    surface = widget.surfaceBuilder();
    unawaited(widget.controller.initialize());
  }

  @override
  void dispose() {
    widget.controller.surfaceRemoved();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller,
          s = c.session,
          l = AppLocalizations.of(context);
      final busy = [
        ViewerPhase.downloading,
        ViewerPhase.decoding,
        ViewerPhase.uploading,
        ViewerPhase.firstFrame,
      ].contains(s.phase);
      return PopScope(
        canPop: !c.fullscreen,
        onPopInvokedWithResult: (didPop, result) {
          if (!didPop) c.setFullscreen(false);
        },
        child: Scaffold(
          backgroundColor: const Color(0xff11151a),
          appBar: c.fullscreen
              ? null
              : AppBar(
                  title: Text(s.active?.fileName ?? l.viewer),
                  actions: [
                    IconButton(
                      tooltip: l.viewerFullscreen,
                      onPressed: () => c.setFullscreen(true),
                      icon: const Icon(Icons.fullscreen),
                    ),
                    IconButton(
                      tooltip: l.viewerClose,
                      onPressed: c.close,
                      icon: const Icon(Icons.close),
                    ),
                  ],
                ),
          body: SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final pad = constraints.maxWidth >= 600;
                Widget status = Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (c.fullscreen)
                      Align(
                        alignment: Alignment.centerRight,
                        child: IconButton(
                          key: const Key('exit-viewer-fullscreen'),
                          tooltip: l.viewerExitFullscreen,
                          onPressed: () => c.setFullscreen(false),
                          icon: const Icon(Icons.fullscreen_exit),
                        ),
                      ),
                    Text(s.active?.fileName ?? l.viewerEmpty),
                    if (s.phase == ViewerPhase.unsupported)
                      Text(l.viewerUnsupported),
                    if (s.phase == ViewerPhase.recovering)
                      Text(l.viewerRecover),
                    if (s.phase == ViewerPhase.closing) Text(l.viewerClosing),
                    if (busy) ...[
                      Text(switch (s.phase) {
                        ViewerPhase.downloading => l.viewerDownload,
                        ViewerPhase.decoding => l.viewerDecode,
                        _ => l.viewerUpload,
                      }),
                      if (s.phase == ViewerPhase.downloading && s.bytes > 0)
                        Text('${s.bytes} / ${s.total ?? "?"} B'),
                      Text(
                        '${l.viewerDownload} → ${l.viewerDecode} → ${l.viewerUpload} → ${l.viewerFirstFrame}',
                      ),
                    ],
                    if (s.phase == ViewerPhase.ready) Text(l.viewerFirstFrame),
                    if (s.error case final error?)
                      Semantics(
                        liveRegion: true,
                        child: Text(errorText(l, error)),
                      ),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        OutlinedButton(
                          key: const Key('viewer-open-local'),
                          onPressed: s.phase == ViewerPhase.unsupported
                              ? null
                              : () => c.open(const LocalModelSource()),
                          child: Text(l.viewerOpenLocal),
                        ),
                        OutlinedButton(
                          key: const Key('viewer-choose-cloud'),
                          onPressed: s.phase == ViewerPhase.unsupported
                              ? null
                              : widget.onChooseCloud,
                          child: Text(l.viewerChooseCloud),
                        ),
                        if (busy || s.phase == ViewerPhase.failed)
                          TextButton(
                            onPressed: c.cancel,
                            child: Text(l.viewerCancel),
                          ),
                        if (c.fullscreen)
                          TextButton(
                            onPressed: c.close,
                            child: Text(l.viewerClose),
                          ),
                      ],
                    ),
                    if (s.active != null) Text(l.viewerConfigFit),
                  ],
                );
                return Theme(
                  data: Theme.of(context).copyWith(
                    brightness: Brightness.dark,
                    textTheme: Theme.of(context).textTheme.apply(
                      bodyColor: Colors.white,
                      displayColor: Colors.white,
                    ),
                    outlinedButtonTheme: OutlinedButtonThemeData(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white,
                      ),
                    ),
                    textButtonTheme: TextButtonThemeData(
                      style: TextButton.styleFrom(
                        foregroundColor: Colors.white,
                      ),
                    ),
                    iconTheme: const IconThemeData(color: Colors.white),
                  ),
                  child: Stack(
                    children: [
                      Positioned.fill(child: surface),
                      Align(
                        alignment: Alignment.topLeft,
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            maxWidth: pad ? 480 : double.infinity,
                            maxHeight: constraints.maxHeight * .62,
                          ),
                          child: SingleChildScrollView(
                            padding: const EdgeInsets.all(12),
                            child: Material(
                              color: const Color(0xe611151a),
                              borderRadius: BorderRadius.circular(12),
                              child: Padding(
                                padding: const EdgeInsets.all(12),
                                child: status,
                              ),
                            ),
                          ),
                        ),
                      ),
                      if (pad)
                        Align(
                          alignment: Alignment.bottomRight,
                          child: ConstrainedBox(
                            constraints: BoxConstraints(
                              maxWidth: 480,
                              maxHeight: constraints.maxHeight * .36,
                            ),
                            child: SingleChildScrollView(
                              padding: const EdgeInsets.all(12),
                              child: Material(
                                color: const Color(0xed11151a),
                                child: Padding(
                                  padding: const EdgeInsets.all(12),
                                  child: ViewerTools(controller: c),
                                ),
                              ),
                            ),
                          ),
                        )
                      else
                        Align(
                          alignment: Alignment.bottomCenter,
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: FilledButton.icon(
                              key: const Key('viewer-tools'),
                              icon: const Icon(Icons.tune),
                              label: Text(l.viewerTools),
                              onPressed: () => showModalBottomSheet<void>(
                                context: context,
                                useSafeArea: true,
                                isScrollControlled: true,
                                builder: (context) => ConstrainedBox(
                                  constraints: BoxConstraints(
                                    maxHeight:
                                        MediaQuery.sizeOf(context).height * .75,
                                  ),
                                  child: SingleChildScrollView(
                                    padding: const EdgeInsets.all(24),
                                    child: ListenableBuilder(
                                      listenable: c,
                                      builder: (context, _) =>
                                          ViewerTools(controller: c),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
        ),
      );
    },
  );
}
