final class ViewerCapabilities {
  const ViewerCapabilities({required this.supported});
  final bool supported;
}

enum ViewerPhase {
  empty,
  downloading,
  decoding,
  uploading,
  firstFrame,
  ready,
  recovering,
  failed,
  unsupported,
  closing,
}

enum ViewerCamera { fixed, free, fit, reset, flip, dolly, fly }
