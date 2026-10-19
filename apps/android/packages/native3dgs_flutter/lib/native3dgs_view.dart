library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

export 'src/native3dgs_controller.dart';
export 'src/native3dgs_state.dart';
import 'src/native3dgs_controller.dart';

/// Development host using Android hybrid composition for the SDK SurfaceView.
class Native3dgsView extends StatefulWidget {
  const Native3dgsView({super.key, required this.onCreated});
  final ValueChanged<Native3dgsController> onCreated;
  @override
  State<Native3dgsView> createState() => _Native3dgsViewState();
}

class _Native3dgsViewState extends State<Native3dgsView> {
  static int _serial = 0;
  late final String _session =
      '${DateTime.now().microsecondsSinceEpoch}-${_serial++}';
  Native3dgsController? _controller;
  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return const Center(child: Text('Native3DGS P0 requires Android arm64.'));
    }
    return PlatformViewLink(
      viewType: 'native3dgs/view',
      surfaceFactory: (context, controller) => AndroidViewSurface(
        controller: controller as AndroidViewController,
        hitTestBehavior: PlatformViewHitTestBehavior.opaque,
        gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
          Factory<EagerGestureRecognizer>(EagerGestureRecognizer.new),
        },
      ),
      onCreatePlatformView: (params) =>
          PlatformViewsService.initExpensiveAndroidView(
              id: params.id,
              viewType: 'native3dgs/view',
              layoutDirection: TextDirection.ltr,
              creationParams: {'sessionId': _session, 'protocolVersion': 1},
              creationParamsCodec: const StandardMessageCodec(),
              onFocus: () => params.onFocusChanged(true),
            )
            ..addOnPlatformViewCreatedListener(params.onPlatformViewCreated)
            ..addOnPlatformViewCreatedListener((id) {
              final controller = Native3dgsController(
                viewId: id,
                sessionId: _session,
              );
              _controller = controller;
              widget.onCreated(controller);
            })
            ..create(),
    );
  }

  @override
  void dispose() {
    unawaited(_controller?.dispose());
    super.dispose();
  }
}
