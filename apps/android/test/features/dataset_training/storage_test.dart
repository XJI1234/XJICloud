import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/oss_upload_adapter.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/local_upload_checkpoint.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/upload_checkpoint_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';

import 'submit_dataset_test.dart' show manifest;

class Transport implements HttpClientAdapter {
  RequestOptions? sent;
  int status = 200;
  int count = 0;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    sent = options;
    count++;
    if (requestStream != null) await requestStream.drain<void>();
    return ResponseBody.fromString('', status);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test('OSS has no Bearer, forbids redirects and maps signature expiry without retry', () async {
    final transport = Transport();
    final oss = OssUploadAdapter(dio: Dio()..httpClientAdapter = transport);
    addTearDown(oss.close);
    expect(
      await oss.put(
        'https://oss.test/image?Signature=example',
        'image/jpeg',
        1,
        Stream.value([1]),
        Cancellation(),
        (_) {},
      ),
      isA<Success<void>>(),
    );
    expect(
      transport.sent!.headers.keys.map((k) => k.toLowerCase()),
      isNot(contains('authorization')),
    );
    expect(transport.sent!.followRedirects, isFalse);
    transport.status = 403;
    final expired = await oss.put(
      'https://oss.test/image',
      'image/jpeg',
      1,
      Stream.value([1]),
      Cancellation(),
      (_) {},
    );
    expect((expired as Failure<void>).error.code, 'signature_expired');
    transport.status = 302;
    expect(
      (await oss.put(
        'https://oss.test/image',
        'image/jpeg',
        1,
        Stream.value([1]),
        Cancellation(),
        (_) {},
      ) as Failure<void>).error.code,
      'redirect',
    );
    expect(transport.count, 3);
    expect(
      await oss.put(
        'http://oss.test/image',
        'image/jpeg',
        1,
        Stream.value([1]),
        Cancellation(),
        (_) {},
      ),
      isA<Failure<void>>(),
    );
    expect(transport.count, 3);
  });
  test('checkpoint roundtrips private identities and confirmed objects; no signed URLs', () async {
    final root = await Directory.systemTemp.createTemp('xji-dataset-journal-');
    addTearDown(() => root.delete(recursive: true));
    final store = LocalUploadCheckpoint(() => Future.value(root.path));
    final state = DatasetCheckpoint(
      userId: 'u',
      projectId: 'p',
      name: 'scan',
      manifest: manifest(),
      jobId: 'j',
      phase: DatasetUploadPhase.uploading,
      confirmed: {'0001.jpg'},
    );
    expect(await store.write(state), isA<Success<void>>());
    final cold = LocalUploadCheckpoint(() => Future.value(root.path));
    final loaded = (await cold.read('u') as Success<DatasetCheckpoint?>).value!;
    expect(loaded.confirmed, {'0001.jpg'});
    expect(loaded.manifest.files.first.source.fingerprint, 'hash1');
    expect(
      (await cold.read('other') as Success<DatasetCheckpoint?>).value,
      isNull,
    );
    final contents = await Stream.fromIterable(
      await root.list().where((e) => e is File).cast<File>().toList(),
    ).asyncMap((f) => f.readAsString()).join();
    expect(contents, isNot(contains('https://')));
    expect(contents, isNot(contains('Signature')));
    expect(await cold.remove('u'), isA<Success<void>>());
    expect(
      (await store.read('u') as Success<DatasetCheckpoint?>).value,
      isNull,
    );
  });
}
