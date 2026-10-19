import 'package:dio/dio.dart';

import '../../features/identity/domain/session_access.dart';
import '../domain/domain_error.dart';
import '../domain/result.dart';

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
    final session = sessions.session;
    if (authenticated && session == null) {
      return const Failure(DomainError('unauthorized'));
    }
    if (authenticated && session!.isExpired(DateTime.now())) {
      await sessions.invalidate(expectedGeneration: generation);
      return const Failure(DomainError('expired'));
    }
    try {
      final response = await _dio.requestUri<Object?>(
        uri,
        data: body,
        options: Options(
          method: method,
          headers: {
            if (authenticated) 'Authorization': 'Bearer ${session!.token}',
          },
          followRedirects: false,
          validateStatus: (_) => true,
          sendTimeout: const Duration(seconds: 20),
          receiveTimeout: const Duration(seconds: 20),
          responseType: ResponseType.json,
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
      if (status == 429) return const Failure(DomainError('rate_limit'));
      final data = response.data;
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
    } on DioException {
      return const Failure(DomainError('network'));
    } on FormatException {
      return const Failure(DomainError('protocol'));
    }
  }

  void close() => _dio.close();
}
