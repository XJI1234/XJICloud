import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/training_job_repository.dart';
import '../domain/upload_checkpoint_port.dart';
import '../domain/training_job.dart';
import 'submit_dataset.dart';

final class ResumeDataset {
  const ResumeDataset(this.submit);
  final SubmitDataset submit;
  Future<Result<TrainingJob>> call(
    DatasetCheckpoint state,
    DatasetTicket? ticket,
    int generation,
    Cancellation cancellation,
    bool Function() current, {
    bool retryCompletion = false,
    void Function(DatasetCheckpoint)? onCheckpoint,
    void Function(int, int)? onProgress,
  }) async {
    if (!current() || cancellation.isCancelled) {
      return const Failure(DomainError('cancelled'));
    }
    if (state.jobId == null) {
      return const Failure(DomainError('dataset_create_uncertain'));
    }
    final result = await submit.repository.get(state.jobId!, generation);
    if (!current() || cancellation.isCancelled) {
      return const Failure(DomainError('cancelled'));
    }
    if (result is Failure<TrainingJob>) return result;
    final job = (result as Success<TrainingJob>).value;
    if (job.projectId != state.projectId) {
      return const Failure(DomainError('protocol'));
    }
    if (job.status != JobStatus.uploading) {
      final next = state.copy(phase: DatasetUploadPhase.completed);
      final saved = await submit.checkpoints.write(next);
      if (saved case Failure<void>(:final error)) return Failure(error);
      if (current()) onCheckpoint?.call(next);
      return result;
    }
    if (ticket == null || DateTime.now().isAfter(ticket.expiresAt)) {
      return const Failure(DomainError('dataset_restart_required'));
    }
    return submit.run(
      state,
      ticket,
      generation,
      cancellation,
      current,
      retryCompletion: retryCompletion,
      onCheckpoint: onCheckpoint,
      onProgress: onProgress,
    );
  }
}
