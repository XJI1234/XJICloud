import '../../../shared/domain/result.dart';
import 'training_job.dart';
import 'dataset_manifest.dart';

final class SignedUpload {
  const SignedUpload(this.archivedName, this.url, this.contentType);
  final String archivedName, url, contentType;
}

final class DatasetTicket {
  DatasetTicket(this.jobId, Map<String, SignedUpload> uploads, this.expiresAt)
    : uploads = Map.unmodifiable(uploads);
  final String jobId;
  final Map<String, SignedUpload> uploads;
  final DateTime expiresAt;
}

abstract interface class TrainingJobRepository {
  Future<Result<DatasetTicket>> create(
    String projectId,
    String name,
    DatasetManifest manifest,
    int generation,
  );
  Future<Result<TrainingJob>> complete(
    String projectId,
    String jobId,
    int generation,
  );
  Future<Result<List<TrainingJob>>> list(String projectId, int generation);
  Future<Result<TrainingJob>> get(String jobId, int generation);
  Future<Result<TrainingJob>> cancel(String jobId, int generation);
  Future<Result<void>> delete(String jobId, int generation);
}
