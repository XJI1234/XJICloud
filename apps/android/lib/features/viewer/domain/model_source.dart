import '../../model_asset/domain/model_asset.dart';
import '../../dataset_training/domain/training_output_port.dart';

sealed class ModelSource {
  const ModelSource();
}

final class LocalModelSource extends ModelSource {
  const LocalModelSource();
}

final class CloudModelSource extends ModelSource {
  const CloudModelSource(this.asset);
  final ModelAsset asset;
}

final class TrainingModelSource extends ModelSource {
  const TrainingModelSource(this.output);
  final TrainingOutputRef output;
}

/// The handle is opaque; only infrastructure can turn it into a local URI.
final class LocalModelRef {
  LocalModelRef({
    required this.handle,
    required this.fileName,
    required this.releaseResource,
  });
  final Object handle;
  final String fileName;
  final Future<void> Function() releaseResource;
  bool _released = false;
  Future<void> release() async {
    if (_released) return;
    _released = true;
    await releaseResource();
  }
}
