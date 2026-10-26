import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../domain/job_policy.dart';
import '../domain/training_job.dart';
import '../domain/training_job_repository.dart';

final class MutateJob {
  const MutateJob(this.repository);
  final TrainingJobRepository repository;
  Future<Result<TrainingJob>> cancel(TrainingJob job, int generation) =>
      canCancelJob(job.status)
      ? repository.cancel(job.id, generation)
      : Future.value(const Failure(DomainError('job_state')));
  Future<Result<void>> delete(TrainingJob job, int generation) =>
      canDeleteJob(job.status)
      ? repository.delete(job.id, generation)
      : Future.value(const Failure(DomainError('job_state')));
}
