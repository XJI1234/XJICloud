import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/project/domain/project.dart';
import 'package:xjicloud_mobile/features/project/infrastructure/http_project_repository.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/infrastructure/api_client.dart';

import '../identity/api_client_test.dart' show Transport;
import '../identity/session_policy_test.dart' show MemoryStore, session;

void main() {
  final fixture = {
    'id': '3d1fa7eb-3d29-404b-b6c9-274f77e52de5',
    'name': 'Site',
    'description': null,
    'createdAt': '2026-10-20T01:02:03Z',
  };
  test(
    'CRUD uses exact protected methods and ProjectResponse without single GET',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      addTearDown(sessions.dispose);
      final transport = Transport();
      final api = ApiClient(
        origin: 'https://api.example.test',
        sessions: sessions,
        dio: Dio()..httpClientAdapter = transport,
      );
      addTearDown(api.close);
      final repo = HttpProjectRepository(api);
      transport.body = jsonEncode({
        'success': true,
        'data': [fixture],
      });
      expect(
        (await repo.list() as Success<List<Project>>).value.single.id,
        fixture['id'],
      );
      expect(transport.request!.method, 'GET');
      expect(transport.request!.uri.path, '/api/v1/projects');
      expect(transport.request!.headers['Authorization'], 'Bearer secret');
      transport.body = jsonEncode({'success': true, 'data': fixture});
      expect(await repo.create('Site', 'note'), isA<Success<Project>>());
      expect(transport.request!.method, 'POST');
      expect(transport.request!.data, {'name': 'Site', 'description': 'note'});
      expect(
        await repo.update(fixture['id']!, 'Changed', ''),
        isA<Success<Project>>(),
      );
      expect(transport.request!.method, 'PATCH');
      expect(transport.request!.uri.path, '/api/v1/projects/${fixture['id']}');
      transport.body = jsonEncode({'success': true, 'data': null});
      expect(await repo.delete(fixture['id']!), isA<Success<void>>());
      expect(transport.request!.method, 'DELETE');
      transport.status = 403;
      transport.body = jsonEncode({
        'success': false,
        'message': 'private backend error',
      });
      expect(
        (await repo.list() as Failure<List<Project>>).error.code,
        'forbidden',
      );
      expect(sessions.session, isNotNull);
      transport.status = 200;
      transport.body = jsonEncode({
        'success': true,
        'data': [
          {'id': 'broken'},
        ],
      });
      expect(
        (await repo.list() as Failure<List<Project>>).error.code,
        'protocol',
      );
      transport.fail = true;
      expect(
        (await repo.list() as Failure<List<Project>>).error.code,
        'network',
      );
    },
  );
}
