import '../../../shared/infrastructure/api_client.dart';
import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../domain/training_job.dart';
import '../domain/training_job_repository.dart';
import '../domain/dataset_manifest.dart';
import 'job_mapper.dart';

final class HttpTrainingJobRepository implements TrainingJobRepository {
  HttpTrainingJobRepository(this.api);
  final ApiClient api;
  Future<Result<T>> _request<T>(
    String method,
    String path,
    int generation,
    T Function(Object?) map, {
    Map<String, dynamic>? body,
  }) async {
    final result = await api.request(
      method,
      path,
      authenticated: true,
      expectedGeneration: generation,
      body: body,
    );
    if (result case Failure<Object?>(:final error)) return Failure(error);
    try {
      return Success(map((result as Success<Object?>).value));
    } on FormatException {
      return const Failure(DomainError('protocol'));
    }
  }

  Future<Result<T>> _invalid<T>() async =>
      const Failure(DomainError('request'));
  @override
  Future<Result<DatasetTicket>> create(
    String p,
    String name,
    DatasetManifest manifest,
    int g,
  ) => !JobMapper.validId(p)
      ? _invalid()
      : _request(
          'POST',
          '/api/v1/projects/$p/datasets',
          g,
          JobMapper.ticket,
          body: {
            'name': name,
            'files': [
              for (final f in manifest.files)
                {
                  'archivedName': f.archivedName,
                  'originalName': f.source.name,
                  'contentType': f.contentType,
                  'sizeBytes': f.source.sizeBytes,
                },
            ],
          },
        );
  @override
  Future<Result<TrainingJob>> complete(String p, String id, int g) =>
      !JobMapper.validId(p) || !JobMapper.validId(id)
      ? _invalid()
      : _request('POST', '/api/v1/projects/$p/datasets/$id/complete', g, (
          data,
        ) {
          final job = JobMapper.job(data);
          if (job.id != id || job.projectId != p) {
            throw const FormatException('identity');
          }
          return job;
        });
  @override
  Future<Result<List<TrainingJob>>> list(String p, int g) =>
      !JobMapper.validId(p)
      ? _invalid()
      : _request('GET', '/api/v1/projects/$p/jobs', g, (data) {
          if (data is! List) throw const FormatException('list');
          final jobs = data.map(JobMapper.job).toList();
          if (jobs.any((j) => j.projectId != p)) {
            throw const FormatException('owner');
          }
          return jobs;
        });
  @override
  Future<Result<TrainingJob>> get(String id, int g) => !JobMapper.validId(id)
      ? _invalid()
      : _request('GET', '/api/v1/jobs/$id', g, (data) {
          final job = JobMapper.job(data);
          if (job.id != id) throw const FormatException('identity');
          return job;
        });
  @override
  Future<Result<TrainingJob>> cancel(String id, int g) => !JobMapper.validId(id)
      ? _invalid()
      : _request('POST', '/api/v1/jobs/$id/cancel', g, JobMapper.job);
  @override
  Future<Result<void>> delete(String id, int g) => !JobMapper.validId(id)
      ? _invalid()
      : _request('DELETE', '/api/v1/jobs/$id', g, (_) {});
}
