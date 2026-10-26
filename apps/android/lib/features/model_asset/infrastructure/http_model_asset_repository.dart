import '../../../shared/infrastructure/api_client.dart';
import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/model_asset.dart';
import '../domain/model_asset_repository.dart';
import 'model_mapper.dart';

final class HttpModelAssetRepository implements ModelAssetRepository {
  HttpModelAssetRepository(this.api);
  final ApiClient api;
  Future<Result<T>> _map<T>(
    String method,
    String path,
    int generation,
    T Function(Object?) map, {
    Map<String, dynamic>? body,
  }) async {
    final identity =
        path.split('/')[path.contains('/projects/')
            ? 4
            : path.contains('/upload-sessions/')
            ? 5
            : 4];
    if (!ModelMapper.validId(identity)) {
      return const Failure(DomainError('request'));
    }
    final response = await api.request(
      method,
      path,
      body: body,
      authenticated: true,
      expectedGeneration: generation,
    );
    if (response case Failure<Object?>(:final error)) return Failure(error);
    try {
      return Success(map((response as Success<Object?>).value));
    } on FormatException {
      return const Failure(DomainError('protocol'));
    }
  }

  @override
  Future<Result<List<ModelAsset>>> list(
    String projectId,
    int generation,
  ) async {
    if (!ModelMapper.validId(projectId)) {
      return const Failure(DomainError('request'));
    }
    return _map('GET', '/api/v1/projects/$projectId/models', generation, (
      data,
    ) {
      if (data is! List) throw const FormatException('list');
      final models = data.map(ModelMapper.fromJson).toList();
      if (models.any((m) => m.projectId != projectId)) {
        throw const FormatException('owner');
      }
      return models;
    });
  }

  @override
  Future<Result<UploadSession>> createUploadSession(
    String projectId,
    String fileName,
    int sizeBytes,
    int generation,
  ) => _map(
    'POST',
    '/api/v1/projects/$projectId/models/upload-sessions',
    generation,
    ModelMapper.session,
    body: {'fileName': fileName, 'sizeBytes': sizeBytes},
  );
  @override
  Future<Result<UploadSession>> getUploadSession(String id, int generation) =>
      _map(
        'GET',
        '/api/v1/models/upload-sessions/$id',
        generation,
        ModelMapper.session,
      );
  @override
  Future<Result<int>> putChunk(
    String id,
    int start,
    int endExclusive,
    int total,
    Stream<List<int>> bytes,
    int generation,
    Cancellation cancellation,
  ) async {
    if (!ModelMapper.validId(id) ||
        start < 0 ||
        endExclusive <= start ||
        endExclusive > total) {
      return const Failure(DomainError('request'));
    }
    final result = await api.request(
      'PUT',
      '/api/v1/models/upload-sessions/$id/chunks',
      authenticated: true,
      expectedGeneration: generation,
      rawBody: bytes,
      contentLength: endExclusive - start,
      contentRange: 'bytes $start-${endExclusive - 1}/$total',
      cancellation: cancellation,
    );
    if (result case Failure<Object?>(:final error)) return Failure(error);
    final data = (result as Success<Object?>).value;
    return data is Map<String, dynamic> && data['receivedBytes'] is int
        ? Success(data['receivedBytes'] as int)
        : const Failure(DomainError('protocol'));
  }

  @override
  Future<Result<ModelAsset>> completeUpload(String id, int generation) => _map(
    'POST',
    '/api/v1/models/upload-sessions/$id/complete',
    generation,
    ModelMapper.fromJson,
  );
  @override
  Future<Result<void>> abortUpload(String id, int generation) =>
      _map('DELETE', '/api/v1/models/upload-sessions/$id', generation, (_) {});
  @override
  Future<Result<void>> delete(String id, int generation) =>
      _map('DELETE', '/api/v1/models/$id', generation, (_) {});
  @override
  Future<Result<void>> downloadToFile(
    String id,
    int generation,
    Future<void> Function(Stream<List<int>>) consume,
    Cancellation cancellation,
  ) async {
    if (!ModelMapper.validId(id)) return const Failure(DomainError('request'));
    final result = await api.request(
      'GET',
      '/api/v1/models/$id/download',
      authenticated: true,
      expectedGeneration: generation,
      consume: consume,
      cancellation: cancellation,
    );
    return switch (result) {
      Success<Object?>() => const Success(null),
      Failure<Object?>(:final error) => Failure(error),
    };
  }
}
