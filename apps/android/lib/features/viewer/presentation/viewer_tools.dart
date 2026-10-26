import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

import '../../../presentation/widgets/press_feedback.dart';

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
      ViewerCamera command,
      IconData icon, [
      List<double> values = const [],
    ]) => PressFeedback(
      enabled: ready,
      child: IconButton(
        tooltip: label,
        onPressed: ready ? () => c.camera(command, values) : null,
        icon: Icon(icon),
      ),
    );
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        button(
          c.session.free ? l.viewerFixed : l.viewerFree,
          c.session.free ? ViewerCamera.fixed : ViewerCamera.free,
          c.session.free ? CupertinoIcons.lock : CupertinoIcons.move,
        ),
        button(l.viewerFit, ViewerCamera.fit, CupertinoIcons.viewfinder),
        button(
          l.viewerReset,
          ViewerCamera.reset,
          CupertinoIcons.arrow_counterclockwise,
        ),
        button(
          l.viewerFlip,
          ViewerCamera.flip,
          CupertinoIcons.arrow_up_arrow_down,
          [c.session.flipped ? 0 : 1],
        ),
        button(l.viewerZoomIn, ViewerCamera.dolly, CupertinoIcons.zoom_in, [
          .8,
        ]),
        button(l.viewerZoomOut, ViewerCamera.dolly, CupertinoIcons.zoom_out, [
          -.8,
        ]),
        if (c.session.free && ready) ...[
          FlyControl(
            label: l.viewerForward,
            icon: CupertinoIcons.arrow_up_right,
            controller: c,
            direction: const [0, 0, 1],
          ),
          FlyControl(
            label: l.viewerBackward,
            icon: CupertinoIcons.arrow_down_left,
            controller: c,
            direction: const [0, 0, -1],
          ),
          FlyControl(
            label: l.viewerLeft,
            icon: CupertinoIcons.arrow_left,
            controller: c,
            direction: const [-1, 0, 0],
          ),
          FlyControl(
            label: l.viewerRight,
            icon: CupertinoIcons.arrow_right,
            controller: c,
            direction: const [1, 0, 0],
          ),
          FlyControl(
            label: l.viewerUp,
            icon: CupertinoIcons.arrow_up,
            controller: c,
            direction: const [0, 1, 0],
          ),
          FlyControl(
            label: l.viewerDown,
            icon: CupertinoIcons.arrow_down,
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
    required this.icon,
    required this.controller,
    required this.direction,
  });
  final String label;
  final IconData icon;
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
    child: PressFeedback(
      child: IconButton(
        tooltip: widget.label,
        onPressed: move,
        icon: Icon(widget.icon),
      ),
    ),
  );
}
