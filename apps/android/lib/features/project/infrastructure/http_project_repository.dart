import '../../../shared/infrastructure/api_client.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/project.dart';
import '../domain/project_repository.dart';
import 'project_mapper.dart';

final class HttpProjectRepository implements ProjectRepository {
  const HttpProjectRepository(this.api);
  final ApiClient api;
  Future<Result<T>> _map<T>(
    Future<Result<Object?>> request,
    T Function(Object?) map,
  ) async {
    final result = await request;
    if (result case Failure<Object?>(:final error)) return Failure(error);
    try {
      return Success(map((result as Success<Object?>).value));
    } on FormatException {
      return const Failure(DomainError('protocol'));
    } on TypeError {
      return const Failure(DomainError('protocol'));
    }
  }

  @override
  Future<Result<List<Project>>> list() => _map(
    api.request('GET', '/api/v1/projects', authenticated: true),
    (value) => (value as List).map(ProjectMapper.fromJson).toList(),
  );
  @override
  Future<Result<Project>> create(String name, String description) => _map(
    api.request(
      'POST',
      '/api/v1/projects',
      authenticated: true,
      body: {'name': name, 'description': description},
    ),
    ProjectMapper.fromJson,
  );
  @override
  Future<Result<Project>> update(String id, String name, String description) {
    if (!ProjectMapper.idPattern.hasMatch(id)) {
      return Future.value(const Failure(DomainError('project_unavailable')));
    }
    return _map(
      api.request(
        'PATCH',
        '/api/v1/projects/$id',
        authenticated: true,
        body: {'name': name, 'description': description},
      ),
      ProjectMapper.fromJson,
    );
  }

  @override
  Future<Result<void>> delete(String id) async {
    if (!ProjectMapper.idPattern.hasMatch(id)) {
      return const Failure(DomainError('project_unavailable'));
    }
    final result = await api.request(
      'DELETE',
      '/api/v1/projects/$id',
      authenticated: true,
    );
    return switch (result) {
      Failure<Object?>(:final error) => Failure(error),
      Success<Object?>() => const Success(null),
    };
  }
}
