import 'dart:convert';
import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/shared/infrastructure/api_client.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';

import 'session_policy_test.dart' show MemoryStore, session;

class Transport implements HttpClientAdapter {
  RequestOptions? request;
  int status = 200;
  String body = '{"success":true,"message":"ok","data":{"needCaptcha":true}}';
  bool fail = false;
  Completer<void>? gate;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    await gate?.future;
    if (fail) {
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.connectionError,
      );
    }
    return ResponseBody.fromString(
      body,
      status,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test('authenticated success arriving after logout cannot publish old account data', () async {
    final transport = Transport()..gate = Completer();
    final c = SessionCoordinator(MemoryStore(), now: () => DateTime.utc(2029));
    addTearDown(c.dispose);
    await c.activate(session());
    final api = ApiClient(
      origin: 'https://api.example.test',
      sessions: c,
      dio: Dio()..httpClientAdapter = transport,
    );
    final request = api.request('GET', '/api/v1/projects', authenticated: true);
    await Future<void>.delayed(Duration.zero);
    await c.invalidate();
    transport.gate!.complete();
    final result = await request;
    expect(result, isA<Failure<Object?>>());
    expect((result as Failure).error.code, 'cancelled');
  });
  test(
    'late API 401 is tied to sent generation, not a newly logged in user',
    () async {
      final transport = Transport()
        ..gate = Completer()
        ..status = 401
        ..body = '{"success":false,"message":"unauthorized","data":null}';
      final c = SessionCoordinator(
        MemoryStore(),
        now: () => DateTime.utc(2029),
      );
      addTearDown(c.dispose);
      await c.activate(session());
      final api = ApiClient(
        origin: 'https://api.example.test',
        sessions: c,
        dio: Dio()..httpClientAdapter = transport,
      );
      final response = api.request(
        'GET',
        '/api/v1/projects',
        authenticated: true,
      );
      await Future<void>.delayed(Duration.zero);
      await c.invalidate();
      await c.activate(session('bob'));
      transport.gate!.complete();
      await response;
      expect(c.session?.username, 'bob');
      expect(transport.request!.headers['Authorization'], 'Bearer secret');
    },
  );
  test('expired credential cannot be sent even before timer fires', () async {
    final transport = Transport();
    final c = SessionCoordinator(MemoryStore(), now: () => DateTime.utc(2019));
    addTearDown(c.dispose);
    await c.activate(
      UserSession(
        userId: 'alice',
        username: 'alice',
        displayName: 'Alice',
        token: 'secret',
        expiresAt: DateTime.utc(2020),
      ),
    );
    final api = ApiClient(
      origin: 'https://api.example.test',
      sessions: c,
      dio: Dio()..httpClientAdapter = transport,
    );
    expect(
      await api.request('GET', '/api/v1/projects', authenticated: true),
      isA<Failure<Object?>>(),
    );
    expect(transport.request, isNull);
    expect(c.session, isNull);
  });
  test(
    'encodes query, unwraps once and never authenticates public login',
    () async {
      final transport = Transport();
      final c = SessionCoordinator(
        MemoryStore(),
        now: () => DateTime.utc(2029),
      );
      addTearDown(c.dispose);
      await c.activate(session());
      final api = ApiClient(
        origin: 'http://127.0.0.1:8080',
        sessions: c,
        dio: Dio()..httpClientAdapter = transport,
        allowDebugHttp: true,
      );
      expect(
        await api.request(
          'GET',
          '/api/v1/auth/need-captcha',
          query: {'username': 'a&x=1'},
        ),
        isA<Success<Object?>>(),
      );
      expect(transport.request!.uri.queryParameters['username'], 'a&x=1');
      expect(transport.request!.headers['Authorization'], isNull);
      expect(transport.request!.followRedirects, isFalse);
    },
  );
  test(
    'rejects origin escapes, role paths and path tricks before sending',
    () async {
      final transport = Transport();
      final c = SessionCoordinator(MemoryStore());
      addTearDown(c.dispose);
      final api = ApiClient(
        origin: 'https://api.example.test',
        sessions: c,
        dio: Dio()..httpClientAdapter = transport,
      );
      for (final path in [
        'https://evil.test/api/v1/projects',
        '//evil.test/api/v1/projects',
        '/api/v1/admin/jobs',
        '/api/v1/worker/jobs',
        '/api/v1/projects/../admin/jobs',
        '/api/v1/%61dmin/jobs',
        '/api/v1/projects?secret=a',
      ]) {
        expect(
          await api.request('GET', path, authenticated: true),
          isA<Failure<Object?>>(),
          reason: path,
        );
      }
      expect(transport.request, isNull);
      expect(
        () => ApiClient(origin: 'http://api.example.test', sessions: c),
        throwsArgumentError,
      );
      expect(
        () => ApiClient(origin: 'https://api.example.test/api/v1', sessions: c),
        throwsArgumentError,
      );
    },
  );
  test('network failure and real backend 403 preserve token; authenticated 401 clears', () async {
    final transport = Transport();
    final c = SessionCoordinator(MemoryStore(), now: () => DateTime.utc(2029));
    addTearDown(c.dispose);
    await c.activate(session());
    final api = ApiClient(
      origin: 'https://api.example.test',
      sessions: c,
      dio: Dio()..httpClientAdapter = transport,
    );
    transport.fail = true;
    expect(
      await api.request('GET', '/api/v1/projects', authenticated: true),
      isA<Failure<Object?>>(),
    );
    expect(c.session, isNotNull);
    transport.fail = false;
    transport.status = 403;
    transport.body = '';
    expect(
      await api.request('GET', '/api/v1/projects', authenticated: true),
      isA<Failure<Object?>>(),
    );
    expect(c.session, isNotNull);
    transport.status = 401;
    transport.body = jsonEncode({
      'success': false,
      'message': '用户名或密码错误',
      'data': null,
    });
    await api.request('POST', '/api/v1/auth/login');
    expect(c.session, isNotNull);
    await api.request('GET', '/api/v1/projects', authenticated: true);
    expect(c.session, isNull);
  });
}
