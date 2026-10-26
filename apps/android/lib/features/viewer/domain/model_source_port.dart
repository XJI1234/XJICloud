import '../../../shared/domain/cancellation.dart';
import '../../../shared/domain/result.dart';
import 'model_source.dart';

abstract interface class ModelSourcePort {
  Future<Result<LocalModelRef>> resolve(
    ModelSource source,
    Cancellation cancellation, {
    void Function(int, int?)? onProgress,
  });
}
