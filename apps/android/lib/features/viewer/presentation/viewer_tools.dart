import 'dart:async';

import 'package:flutter/material.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../domain/viewer_capabilities.dart';
import 'viewer_controller.dart';

class ViewerTools extends StatelessWidget {
  const ViewerTools({super.key, required this.controller});
  final ViewerController controller;
  @override
  Widget build(BuildContext context) {
    final c = controller,
        l = AppLocalizations.of(context),
        ready = c.session.active != null;
    Widget button(
      String label,
      ViewerCamera command, [
      List<double> values = const [],
    ]) => OutlinedButton(
      onPressed: ready ? () => c.camera(command, values) : null,
      child: Text(label),
    );
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        button(
          c.session.free ? l.viewerFixed : l.viewerFree,
          c.session.free ? ViewerCamera.fixed : ViewerCamera.free,
        ),
        button(l.viewerFit, ViewerCamera.fit),
        button(l.viewerReset, ViewerCamera.reset),
        button(l.viewerFlip, ViewerCamera.flip, [c.session.flipped ? 0 : 1]),
        button(l.viewerZoomIn, ViewerCamera.dolly, [.8]),
        button(l.viewerZoomOut, ViewerCamera.dolly, [-.8]),
        if (c.session.free && ready) ...[
          FlyControl(
            label: l.viewerForward,
            controller: c,
            direction: const [0, 0, 1],
          ),
          FlyControl(
            label: l.viewerBackward,
            controller: c,
            direction: const [0, 0, -1],
          ),
          FlyControl(
            label: l.viewerLeft,
            controller: c,
            direction: const [-1, 0, 0],
          ),
          FlyControl(
            label: l.viewerRight,
            controller: c,
            direction: const [1, 0, 0],
          ),
          FlyControl(
            label: l.viewerUp,
            controller: c,
            direction: const [0, 1, 0],
          ),
          FlyControl(
            label: l.viewerDown,
            controller: c,
            direction: const [0, -1, 0],
          ),
        ],
      ],
    );
  }
}

/// Holding a direction sends real delta-time fly commands; releasing stops it.
class FlyControl extends StatefulWidget {
  const FlyControl({
    super.key,
    required this.label,
    required this.controller,
    required this.direction,
  });
  final String label;
  final ViewerController controller;
  final List<double> direction;
  @override
  State<FlyControl> createState() => _FlyControlState();
}

class _FlyControlState extends State<FlyControl> {
  Timer? timer;
  void stop() {
    timer?.cancel();
    timer = null;
  }

  void move() {
    final c = widget.controller;
    if (!c.visible || !c.session.free) {
      stop();
      return;
    }
    unawaited(c.camera(ViewerCamera.fly, [...widget.direction, .05]));
  }

  void start() {
    stop();
    move();
    timer = Timer.periodic(const Duration(milliseconds: 50), (_) => move());
  }

  @override
  void dispose() {
    stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Listener(
    onPointerDown: (_) => start(),
    onPointerUp: (_) => stop(),
    onPointerCancel: (_) => stop(),
    child: OutlinedButton(onPressed: move, child: Text(widget.label)),
  );
}
