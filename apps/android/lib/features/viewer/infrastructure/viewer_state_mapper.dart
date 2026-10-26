import 'package:native3dgs_flutter/native3dgs_view.dart';

import '../domain/viewer_engine_port.dart';
import '../domain/viewer_capabilities.dart';

ViewerEngineState mapViewerState(Native3dgsState state) => ViewerEngineState(
  switch (state.phase) {
    'Loading' => ViewerPhase.decoding,
    'Uploading' => ViewerPhase.uploading,
    'Ready' => ViewerPhase.ready,
    'Recovering' => ViewerPhase.recovering,
    'Failed' => ViewerPhase.failed,
    'Unsupported' => ViewerPhase.unsupported,
    'Stopping' => ViewerPhase.closing,
    _ => ViewerPhase.empty,
  },
  requestId: state.requestId,
  activeRequestId: state.activeRequestId,
);
