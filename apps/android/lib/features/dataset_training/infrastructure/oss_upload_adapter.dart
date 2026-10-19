import 'dart:io';

import 'package:dio/dio.dart';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/object_storage_port.dart';
import 'job_mapper.dart';

/// Independent transport: no API interceptors, credentials or redirect following.
final class OssUploadAdapter implements ObjectStoragePort {
  OssUploadAdapter({Dio? dio}) : _dio = dio ?? Dio() {
    _dio.options.headers.clear();
    _dio.options.connectTimeout = const Duration(seconds: 20);
  }
  final Dio _dio;
  Future<Result<void>> _send(
    String method,
    String url,
    Cancellation cancellation, {
    String? type,
    int? length,
    Stream<List<int>>? bytes,
    void Function(int)? progress,
    Future<void> Function(Stream<List<int>>, int?)? consume,
  }) async {
    final token = CancelToken();
    final detach = cancellation.listen(() => token.cancel());
    Stream<List<int>> guarded(Stream<List<int>> source) async* {
      await for (final chunk in source) {
        if (cancellation.isCancelled) throw const DomainError('cancelled');
        yield chunk;
      }
    }

    try {
      final uri = JobMapper.signedUrl(url);
      if (cancellation.isCancelled) {
        return const Failure(DomainError('cancelled'));
      }
      final response = await _dio.requestUri<ResponseBody>(
        uri,
        data: bytes == null ? null : guarded(bytes),
        cancelToken: token,
        options: Options(
          method: method,
          headers: {'Content-Type': ?type, 'Content-Length': ?length},
          followRedirects: false,
          validateStatus: (_) => true,
          responseType: ResponseType.stream,
          sendTimeout: const Duration(seconds: 30),
          receiveTimeout: const Duration(seconds: 30),
        ),
        onSendProgress: (sent, total) {
          if (!cancellation.isCancelled) {
            progress?.call(sent.clamp(0, length ?? sent));
          }
        },
      );
      final status = response.statusCode ?? 0;
      if (status == 401 || status == 403) {
        return const Failure(DomainError('signature_expired'));
      }
      if (status >= 300 && status < 400) {
        return const Failure(DomainError('redirect'));
      }
      if (status < 200 || status >= 300) {
        return Failure(DomainError(status >= 500 ? 'server' : 'request'));
      }
      if (consume != null) {
        await consume(
          guarded(response.data!.stream),
          int.tryParse(response.headers.value('content-length') ?? ''),
        );
      } else {
        var n = 0;
        await for (final chunk in guarded(response.data!.stream)) {
          n += chunk.length;
          if (n > 65536) throw const FormatException('OSS response');
        }
      }
      if (cancellation.isCancelled) {
        return const Failure(DomainError('cancelled'));
      }
      if (length != null) progress?.call(length);
      return const Success(null);
    } on DioException catch (e) {
      return Failure(
        DomainError(CancelToken.isCancel(e) ? 'cancelled' : 'network'),
      );
    } on DomainError catch (e) {
      return Failure(e);
    } on SocketException {
      return const Failure(DomainError('network'));
    } on HttpException {
      return const Failure(DomainError('network'));
    } on TlsException {
      return const Failure(DomainError('network'));
    } on FileSystemException {
      return const Failure(DomainError('storage'));
    } on FormatException {
      return const Failure(DomainError('protocol'));
    } finally {
      token.cancel();
      detach();
    }
  }

  @override
  Future<Result<void>> put(
    String url,
    String type,
    int length,
    Stream<List<int>> bytes,
    Cancellation cancellation,
    void Function(int) onProgress,
  ) => _send(
    'PUT',
    url,
    cancellation,
    type: type,
    length: length,
    bytes: bytes,
    progress: onProgress,
  );
  Future<Result<void>> download(
    String url,
    Cancellation cancellation,
    Future<void> Function(Stream<List<int>>, int?) consume,
  ) => _send('GET', url, cancellation, consume: consume);
  void close() => _dio.close(force: true);
}
