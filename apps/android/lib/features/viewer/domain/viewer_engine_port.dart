import '../../../shared/domain/result.dart';
import 'model_source.dart';
import 'viewer_capabilities.dart';

final class ViewerEngineState {
  const ViewerEngineState(
    this.phase, {
    this.requestId = 0,
    this.activeRequestId = 0,
  });
  final ViewerPhase phase;
  final int requestId, activeRequestId;
}

abstract interface class ViewerEnginePort {
  Stream<ViewerEngineState> get states;

  /// Last native-confirmed active host request, refreshed by cancel acknowledgement.
  int get activeRequestId;
  Future<Result<ViewerCapabilities>> initialize();
  Future<Result<int>> open(LocalModelRef model);
  Future<Result<void>> cancel();
  Future<Result<void>> close();
  Future<Result<void>> visible(bool value);
  Future<Result<void>> fullscreen(bool value);
  Future<Result<void>> camera(
    ViewerCamera command, [
    List<double> values = const [],
  ]);
}
