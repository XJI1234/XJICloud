class Native3dgsState {
  const Native3dgsState({
    this.phase = 'Empty',
    this.requestId = 0,
    this.activeRequestId = 0,
    this.sceneCount = 0,
    this.surfaceGeneration = 0,
    this.error = 0,
    this.message,
    this.supported,
    this.errorOrigin,
  });
  final String phase;
  final int requestId, activeRequestId, sceneCount, surfaceGeneration, error;
  final String? message;
  final bool? supported;
  final String? errorOrigin;
  Native3dgsState withPhase(
    String phase, {
    String? message,
    int error = 0,
    int? requestId,
    String? errorOrigin,
  }) => Native3dgsState(
    phase: phase,
    requestId: requestId ?? this.requestId,
    activeRequestId: activeRequestId,
    sceneCount: sceneCount,
    surfaceGeneration: surfaceGeneration,
    error: error,
    message: message,
    supported: supported,
    errorOrigin: errorOrigin,
  );
  factory Native3dgsState.fromMap(Map<Object?, Object?> data) =>
      Native3dgsState(
        phase: data['phase'] as String? ?? 'Empty',
        requestId: (data['requestId'] as num?)?.toInt() ?? 0,
        activeRequestId: (data['activeRequestId'] as num?)?.toInt() ?? 0,
        sceneCount: (data['sceneCount'] as num?)?.toInt() ?? 0,
        surfaceGeneration: (data['surfaceGeneration'] as num?)?.toInt() ?? 0,
        error: (data['error'] as num?)?.toInt() ?? 0,
        message: data['message'] as String?,
        supported: data['supported'] as bool?,
        errorOrigin: data['errorOrigin'] as String?,
      );
}

class Native3dgsResult {
  const Native3dgsResult({
    required this.ok,
    this.message,
    this.requestId,
    this.uri,
    this.supported,
    this.error = 0,
    this.errorOrigin,
  });
  final bool ok;
  final String? message, uri;
  final int? requestId;
  final bool? supported;
  final int error;
  final String? errorOrigin;
}
