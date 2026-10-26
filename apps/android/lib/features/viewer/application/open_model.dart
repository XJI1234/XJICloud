import '../../../shared/domain/cancellation.dart';
import '../../../shared/domain/result.dart';
import '../domain/model_source.dart';
import '../domain/model_source_port.dart';

final class OpenModel {
  const OpenModel(this.sources);
  final ModelSourcePort sources;
  Future<Result<LocalModelRef>> call(
    ModelSource source,
    Cancellation cancellation, {
    void Function(int, int?)? onProgress,
  }) => sources.resolve(source, cancellation, onProgress: onProgress);
}
