import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/domain/session_access.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/features/identity/infrastructure/http_auth_repository.dart';
import 'package:xjicloud_mobile/shared/infrastructure/api_client.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'auth_mapper_test.dart' show response;

class _Session implements SessionAccess {
  @override
  UserSession? session;
  @override
  int get generation => 0;
  @override
  Future<Result<void>> invalidate({int? expectedGeneration}) async {
    session = null;
    return const Success(null);
  }
}

class _Transport implements HttpClientAdapter {
  RequestOptions? last;
  int status = 200;
  Object? data;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancelFuture,
  ) async {
    last = options;
    return ResponseBody.fromString(
      jsonEncode({'success': status == 200, 'message': 'safe', 'data': data}),
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
  late _Transport transport;
  late _Session sessions;
  late HttpAuthRepository repo;
  setUp(() {
    transport = _Transport();
    sessions = _Session();
    final api = ApiClient(
      origin: 'https://api.example.test',
      sessions: sessions,
      dio: Dio()..httpClientAdapter = transport,
    );
    addTearDown(api.close);
    repo = HttpAuthRepository(api);
  });
  test('registration posts only phone password and six digit SMS proof and returns profile gate session', () async {
    transport.data = {...response(), 'profileRequired': true};
    final result = await repo.register(
      phone: '13800138000',
      password: 'password',
      smsChallengeId: 'sms-id',
      smsCode: '012345',
    );
    expect((result as Success<UserSession>).value.profileRequired, isTrue);
    expect(transport.last!.uri.path, '/api/v1/auth/register');
    expect(transport.last!.data, {
      'phone': '13800138000',
      'password': 'password',
      'smsChallengeId': 'sms-id',
      'smsCode': '012345',
    });
    expect(transport.last!.headers['Authorization'], isNull);
  });
  test('SMS response maps actual expiry/retry contract and malformed challenges fail closed', () async {
    transport.data = {
      'challengeId': 'sms-id',
      'expiresInSeconds': 300,
      'retryAfterSeconds': 60,
    };
    final result = await repo.sendRegistrationSms(
      phone: '13800138000',
      captchaKey: 'image-key',
      captchaCode: '1234',
    );
    expect(transport.last!.uri.path, '/api/v1/auth/registration-sms');
    expect(transport.last!.data, {
      'phone': '13800138000',
      'captchaKey': 'image-key',
      'captchaCode': '1234',
    });
    final challenge = (result as Success).value;
    expect(challenge.retryAfterSeconds, 60);
    expect(challenge.expiresInSeconds, 300);
    for (final malformed in [
      <String, Object>{},
      {'challengeId': 'x', 'expiresInSeconds': 0, 'retryAfterSeconds': 60},
      {'challengeId': 'x', 'expiresInSeconds': 300, 'retryAfterSeconds': -1},
    ]) {
      transport.data = malformed;
      expect(
        await repo.sendRegistrationSms(
          phone: '13800138000',
          captchaKey: 'k',
          captchaCode: '1',
        ),
        isA<Failure>(),
      );
    }
  });
  test(
    'fusion init is public and opaque proof login never includes client phone',
    () async {
      transport.data = {
        'challengeId': 'f-id',
        'authToken': 'sdk-token',
        'schemeCode': 'scheme',
        'templateId': '100001',
        'expiresInSeconds': 900,
      };
      final init = await repo.initializeFusion();
      expect((init as Success).value.schemeCode, 'scheme');
      expect(transport.last!.uri.path, '/api/v1/auth/fusion/init');
      expect(transport.last!.headers['Authorization'], isNull);
      transport.data = response();
      expect(
        await repo.loginWithFusion(
          challengeId: 'f-id',
          verifyToken: 'opaque+token/=',
        ),
        isA<Success<UserSession>>(),
      );
      expect(transport.last!.uri.path, '/api/v1/auth/fusion/login');
      expect(transport.last!.data, {
        'challengeId': 'f-id',
        'verifyToken': 'opaque+token/=',
      });
      transport.data = {
        'challengeId': 'f-id',
        'authToken': 'sdk-token',
        'expiresInSeconds': 900,
      };
      expect(await repo.initializeFusion(), isA<Failure>());
    },
  );
  test('profile reads and writes require the current user Bearer and map server profile', () async {
    sessions.session = UserSession(
      userId: 'abc',
      username: 'alice',
      displayName: 'Alice',
      token: 'private-token',
      expiresAt: DateTime.utc(2100),
    );
    transport.data = {
      'userId': 'abc',
      'username': 'alice',
      'phone': '13800138000',
      'displayName': 'Updated',
      'email': 'a@example.test',
      'organization': 'Lab',
      'profileRequired': false,
    };
    expect((await repo.getProfile() as Success).value.displayName, 'Updated');
    expect(transport.last!.method, 'GET');
    expect(transport.last!.uri.path, '/api/v1/account');
    expect(transport.last!.headers['Authorization'], 'Bearer private-token');
    await repo.updateProfile(
      displayName: 'Updated',
      email: 'a@example.test',
      organization: 'Lab',
    );
    expect(transport.last!.method, 'PUT');
    expect(transport.last!.headers['Authorization'], 'Bearer private-token');
    expect(transport.last!.data, {
      'displayName': 'Updated',
      'email': 'a@example.test',
      'organization': 'Lab',
    });
  });
  test(
    'server failures remain failures instead of empty successful challenges',
    () async {
      transport.status = 503;
      final init = await repo.initializeFusion();
      expect(init, isA<Failure>());
      final sms = await repo.sendRegistrationSms(
        phone: '13800138000',
        captchaKey: 'k',
        captchaCode: '1',
      );
      expect(sms, isA<Failure>());
    },
  );
}
