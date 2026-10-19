import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';

import '../../features/identity/domain/session_access.dart';
import '../domain/domain_error.dart';
import '../domain/result.dart';
import '../domain/cancellation.dart';

/// The API transport is separate from future OSS clients. It never follows redirects.
final class ApiClient {
  ApiClient({
    required String origin,
    required this.sessions,
    Dio? dio,
    bool allowDebugHttp = false,
  }) : _dio = dio ?? Dio(),
       base = Uri.parse(origin) {
    final loopback = [
      '127.0.0.1',
      'localhost',
      '10.0.2.2',
      '::1',
    ].contains(base.host);
    if (!base.hasAuthority ||
        base.userInfo.isNotEmpty ||
        (base.path.isNotEmpty && base.path != '/') ||
        base.hasQuery ||
        base.hasFragment ||
        (base.scheme != 'https' &&
            !(allowDebugHttp && base.scheme == 'http' && loopback))) {
      throw ArgumentError(
        'API_BASE_URL must be an HTTPS origin (debug loopback HTTP allowed)',
      );
    }
    _dio.options.connectTimeout = const Duration(seconds: 20);
  }
  final Dio _dio;
  final Uri base;
  final SessionAccess sessions;
  Future<Result<Object?>> request(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Map<String, dynamic>? body,
    bool authenticated = false,
    int? expectedGeneration,
    Stream<List<int>>? rawBody,
    int? contentLength,
    String? contentRange,
    Cancellation? cancellation,
    Future<void> Function(Stream<List<int>>)? consume,
  }) async {
    // Path is a decoded API path, never a URL or a pre-encoded user value.
    if (!path.startsWith('/api/v1/') ||
        path.contains(RegExp(r'[%?#\\]')) ||
        path
            .split('/')
            .any(
              (p) => p == '.' || p == '..' || p.isEmpty && path.contains('//'),
            ) ||
        RegExp(r'^/api/v1/(admin|worker)(/|$)').hasMatch(path)) {
      return const Failure(DomainError('forbidden'));
    }
    final uri = base.replace(path: path, queryParameters: query);
    if (uri.origin != base.origin) {
      return const Failure(DomainError('forbidden'));
    }
    final generation = sessions.generation;
    if (expectedGeneration != null && generation != expectedGeneration ||
        cancellation?.isCancelled == true) {
      return const Failure(DomainError('cancelled'));
    }
    final session = sessions.session;
    if (authenticated && session == null) {
      return const Failure(DomainError('unauthorized'));
    }
    if (authenticated && session!.isExpired(DateTime.now())) {
      await sessions.invalidate(expectedGeneration: generation);
      return const Failure(DomainError('expired'));
    }
    final cancelToken = CancelToken();
    final detach = cancellation?.listen(() => cancelToken.cancel());
    final streaming = rawBody != null || consume != null;
    Stream<List<int>> guarded(Stream<List<int>> source) async* {
      await for (final bytes in source) {
        if (cancellation?.isCancelled == true ||
            authenticated && generation != sessions.generation) {
          throw const DomainError('cancelled');
        }
        yield bytes;
      }
    }

    try {
      final response = await _dio.requestUri<Object?>(
        uri,
        data: rawBody == null ? body : guarded(rawBody),
        cancelToken: cancelToken,
        options: Options(
          method: method,
          headers: {
            if (authenticated) 'Authorization': 'Bearer ${session!.token}',
            if (rawBody != null) 'Content-Type': 'application/octet-stream',
            'Content-Length': ?contentLength,
            'Content-Range': ?contentRange,
          },
          followRedirects: false,
          validateStatus: (_) => true,
          sendTimeout: const Duration(seconds: 20),
          receiveTimeout: const Duration(seconds: 20),
          responseType: streaming ? ResponseType.stream : ResponseType.json,
        ),
      );
      final status = response.statusCode ?? 0;
      if (authenticated && generation != sessions.generation && status != 401) {
        return const Failure(DomainError('cancelled'));
      }
      if (status == 401 && authenticated) {
        await sessions.invalidate(expectedGeneration: generation);
      }
      if (status >= 300 && status < 400) {
        return const Failure(DomainError('redirect'));
      }
      if (status == 403) return const Failure(DomainError('forbidden'));
      if (status == 404) return const Failure(DomainError('not_found'));
      if (status == 429) return const Failure(DomainError('rate_limit'));
      if (consume != null && status == 200) {
        await consume(guarded((response.data as ResponseBody).stream));
        if (cancellation?.isCancelled == true ||
            generation != sessions.generation) {
          return const Failure(DomainError('cancelled'));
        }
        return const Success(null);
      }
      Object? data = response.data;
      if (streaming) {
        var length = 0;
        final bytes = <int>[];
        await for (final chunk in guarded((data as ResponseBody).stream)) {
          length += chunk.length;
          if (length > 1024 * 1024) throw const FormatException('response');
          bytes.addAll(chunk);
        }
        data = jsonDecode(utf8.decode(bytes));
      }
      if (data is! Map<String, dynamic> || data['success'] is! bool) {
        return Failure(
          DomainError(status == 401 ? 'unauthorized' : 'protocol'),
        );
      }
      if (data['success'] == true && status >= 200 && status < 300) {
        return Success(data['data']);
      }
      // Only explicitly known business reasons become stable UI codes.
      final message = data['message'];
      final code = message is String && message.contains('验证码')
          ? 'captcha'
          : message == '用户名已存在'
          ? 'username_taken'
          : message == '用户名或密码错误'
          ? 'credentials'
          : status == 401
          ? 'unauthorized'
          : status >= 500
          ? 'server'
          : 'request';
      return Failure(DomainError(code));
    } on DioException catch (error) {
      return Failure(
        DomainError(CancelToken.isCancel(error) ? 'cancelled' : 'network'),
      );
    } on DomainError catch (error) {
      return Failure(error);
    } on SocketException {
      // Response streams can emit IO errors after Dio returns the headers.
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
      // Also closes unread redirect/error bodies from streaming responses.
      cancelToken.cancel();
      detach?.call();
    }
  }

  void close() => _dio.close();
}
