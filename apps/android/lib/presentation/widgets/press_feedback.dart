import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

/// Pointer feedback only: keyboard activation retains a stationary focus target.
/// Retargeting preserves presentation value and velocity, including on release.
class PressFeedback extends StatefulWidget {
  const PressFeedback({super.key, required this.child, this.enabled = true});

  final Widget child;
  final bool enabled;

  @override
  State<PressFeedback> createState() => _PressFeedbackState();
}

class _PressFeedbackState extends State<PressFeedback>
    with SingleTickerProviderStateMixin {
  late final AnimationController scale = AnimationController.unbounded(
    vsync: this,
    value: 1,
  );
  int? pointer;
  Offset? origin;
  bool reduced = false;

  void reset() {
    pointer = null;
    origin = null;
    scale.value = 1;
  }

  void target(double value) {
    scale.animateWith(
      SpringSimulation(
        SpringDescription.withDampingRatio(mass: 1, stiffness: 500, ratio: 1),
        scale.value,
        value,
        scale.velocity,
        tolerance: const Tolerance(distance: .0001, velocity: .001),
      ),
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    reduced = MediaQuery.disableAnimationsOf(context);
    if (reduced) reset();
  }

  @override
  void didUpdateWidget(PressFeedback oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!widget.enabled) reset();
  }

  @override
  void dispose() {
    scale.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Listener(
    onPointerDown: (event) {
      if (!widget.enabled || reduced || pointer != null || event.buttons != 1) {
        return;
      }
      pointer = event.pointer;
      origin = event.position;
      target(.97);
    },
    onPointerMove: (event) {
      if (event.pointer == pointer &&
          (event.position - origin!).distance > 18) {
        pointer = null;
        target(1);
      }
    },
    onPointerUp: (event) {
      if (event.pointer == pointer) {
        pointer = null;
        target(1);
      }
    },
    onPointerCancel: (event) {
      if (event.pointer == pointer) {
        pointer = null;
        target(1);
      }
    },
    child: ScaleTransition(scale: scale, child: widget.child),
  );
}
