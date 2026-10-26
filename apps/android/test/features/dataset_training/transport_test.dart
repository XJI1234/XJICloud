import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/shared/infrastructure/api_client.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/sse_job_event_adapter.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/http_training_job_repository.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'sse_parser_test.dart' show id;

class ApiTransport implements HttpClientAdapter {
  int status = 200;
  Object? data;
  RequestOptions? sent;
  @override
  Future<ResponseBody> fetch(
    RequestOptions o,
    Stream<Uint8List>? s,
    Future<void>? cancel,
  ) async {
    sent = o;
    return ResponseBody.fromString(
      jsonEncode({'success': status == 200, 'data': data}),
      status,
      headers: {
        'content-type': ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test('SSE controlled 401 invalidates account exactly once and emits mapped error', () async {
    final store = MemoryStore(), s = SessionCoordinator(store);
    await s.activate(session());
    addTearDown(s.dispose);
    final transport = ApiTransport()..status = 401,
        api = ApiClient(
          origin: 'https://api.test',
          sessions: s,
          dio: Dio()..httpClientAdapter = transport,
        );
    addTearDown(api.close);
    await expectLater(
      SseJobEventAdapter(api)
          .subscribe(id, s.generation, Cancellation())
          .toList(),
      throwsA(isA<DomainError>().having((e) => e.code, 'code', 'unauthorized')),
    );
    expect(s.session, isNull);
    expect(store.clears, 1);
    expect(transport.sent!.followRedirects, isFalse);
    expect(transport.sent!.headers['Authorization'], 'Bearer secret');
  });
  test(
    'GET list maps real DTO and rejects wrong project instead of empty success',
    () async {
      final s = SessionCoordinator(MemoryStore());
      await s.activate(session());
      addTearDown(s.dispose);
      final transport = ApiTransport()
            ..data = [
              {
                'id': id,
                'projectId': id,
                'name': 'scan',
                'status': 'FAILED',
                'progress': 25,
                'stage': 'training',
                'message': 'failed',
                'downloadUrl': null,
                'errorMessage': 'worker error',
                'createdAt': '2026-10-19T00:00:00Z',
                'updatedAt': '2026-10-19T01:00:00Z',
              },
            ],
          api = ApiClient(
            origin: 'https://api.test',
            sessions: s,
            dio: Dio()..httpClientAdapter = transport,
          );
      addTearDown(api.close);
      final repo = HttpTrainingJobRepository(api),
          result = await repo.list(id, s.generation);
      expect(result, isA<Success>());
      expect((result as Success).value.single.errorMessage, 'worker error');
      final wrong = await repo.list(
        '22222222-2222-2222-2222-222222222222',
        s.generation,
      );
      expect((wrong as Failure).error.code, 'protocol');
      expect(
        (await repo.get('../admin', s.generation) as Failure).error.code,
        'request',
      );
    },
  );
}
