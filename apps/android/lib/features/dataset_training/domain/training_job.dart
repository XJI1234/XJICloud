enum JobStatus {
  pending,
  uploading,
  queued,
  running,
  completed,
  failed,
  cancelled,
}

final class TrainingJob {
  const TrainingJob({
    required this.id,
    required this.projectId,
    required this.name,
    required this.status,
    required this.progress,
    this.stage = '',
    this.message = '',
    this.downloadUrl,
    this.errorMessage,
    required this.createdAt,
    required this.updatedAt,
  });
  final String id, projectId, name, stage, message;
  final String? downloadUrl, errorMessage;
  final JobStatus status;
  final int progress;
  final DateTime createdAt, updatedAt;
  TrainingJob progressUpdate(JobProgress event) => TrainingJob(
    id: id,
    projectId: projectId,
    name: name,
    status: event.status,
    progress: event.progress,
    stage: event.stage,
    message: event.message,
    createdAt: createdAt,
    updatedAt: updatedAt,
  );
}

final class JobProgress {
  const JobProgress({
    required this.jobId,
    required this.status,
    required this.progress,
    this.stage = '',
    this.message = '',
  });
  final String jobId, stage, message;
  final JobStatus status;
  final int progress;
}
