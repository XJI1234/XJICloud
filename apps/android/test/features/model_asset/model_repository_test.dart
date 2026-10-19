import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/infrastructure/api_client.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/http_model_asset_repository.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;

const id = '12345678-1234-1234-1234-123456789abc';

class BinaryTransport implements HttpClientAdapter {
  RequestOptions? request;
  List<int>? sent;
  int status = 200;
  bool binary = false;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    request = options;
    if (stream != null) sent = await stream.expand((x) => x).toList();
    return binary
        ? ResponseBody(
            Stream.fromIterable([
              Uint8List.fromList([1, 2]),
              Uint8List.fromList([3, 4]),
            ]),
            status,
          )
        : ResponseBody.fromString(
            '{"success":true,"data":{"receivedBytes":10}}',
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
  late SessionCoordinator sessions;
  late BinaryTransport transport;
  late ApiClient api;
  late HttpModelAssetRepository repo;
  setUp(() async {
    sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    transport = BinaryTransport();
    api = ApiClient(
      origin: 'https://api.example.test',
      sessions: sessions,
      dio: Dio()..httpClientAdapter = transport,
    );
    repo = HttpModelAssetRepository(api);
  });
  tearDown(() {
    api.close();
    sessions.dispose();
  });
  test(
    'raw tail PUT sends exact Content-Range content length Bearer and bytes',
    () async {
      final result = await repo.putChunk(
        id,
        7,
        10,
        10,
        Stream.value([7, 8, 9]),
        sessions.generation,
        Cancellation(),
      );
      expect((result as Success<int>).value, 10);
      expect(transport.sent, [7, 8, 9]);
      expect(transport.request!.headers['Content-Range'], 'bytes 7-9/10');
      expect(transport.request!.headers['Content-Length'], 3);
      expect(transport.request!.headers['Authorization'], 'Bearer secret');
      expect(transport.request!.followRedirects, isFalse);
    },
  );
  test(
    'download cancellation between streamed buffers stops before second write',
    () async {
      transport.binary = true;
      final cancel = Cancellation(), received = <int>[];
      final result = await repo.downloadToFile(id, sessions.generation, (
        stream,
      ) async {
        await for (final chunk in stream) {
          received.addAll(chunk);
          cancel.cancel();
        }
      }, cancel);
      expect((result as Failure).error.code, 'cancelled');
      expect(received, [1, 2]);
    },
  );
  test('download404 does not invoke sink or masquerade as success', () async {
    transport
      ..binary = true
      ..status = 404;
    var writes = 0;
    final result = await repo.downloadToFile(id, sessions.generation, (
      _,
    ) async {
      writes++;
    }, Cancellation());
    expect((result as Failure).error.code, 'not_found');
    expect(writes, 0);
  });
  test(
    'old generation cannot authenticate transfer as newly signed in identity',
    () async {
      final generation = sessions.generation;
      await sessions.invalidate();
      await sessions.activate(session('bob'));
      final result = await repo.downloadToFile(
        id,
        generation,
        (_) async {},
        Cancellation(),
      );
      expect((result as Failure).error.code, 'cancelled');
      expect(transport.request, isNull);
    },
  );
  test('rejects invalid path identity and cross-origin redirect without writing bytes', () async {
    expect(
      await repo.downloadToFile(
        'not-a-model-id',
        sessions.generation,
        (_) async {},
        Cancellation(),
      ),
      isA<Failure<void>>(),
    );
    expect(transport.request, isNull);
    transport
      ..binary = true
      ..status = 302;
    final result = await repo.downloadToFile(id, sessions.generation, (
      _,
    ) async {
      fail('must not write redirect');
    }, Cancellation());
    expect((result as Failure).error.code, 'redirect');
    expect(transport.request!.followRedirects, isFalse);
  });
}
