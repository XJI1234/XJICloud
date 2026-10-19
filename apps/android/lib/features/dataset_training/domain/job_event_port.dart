import 'training_job.dart';
import '../../../shared/domain/cancellation.dart';

abstract interface class JobEventPort {
  Stream<JobProgress> subscribe(
    String jobId,
    int generation,
    Cancellation cancellation,
  );
}
