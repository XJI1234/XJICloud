import 'training_job.dart';

bool canCancelJob(JobStatus status) =>
    status != JobStatus.completed && status != JobStatus.cancelled;
bool canDeleteJob(JobStatus status) => [
  JobStatus.completed,
  JobStatus.failed,
  JobStatus.cancelled,
].contains(status);
bool needsTerminalSnapshot(JobStatus status) =>
    status == JobStatus.completed || status == JobStatus.failed;
