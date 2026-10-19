import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/preferences_upload_checkpoints.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/android_local_file_adapter.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/upload_checkpoint_port.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

const source = LocalModelRef(
  id: '12345678-1234-1234-1234-123456789abc',
  fileName: 'a.ply',
  sizeBytes: 10,
  fingerprint: 'abc',
);
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));
  test('restart restores owner project source fingerprint and uncertain phase without credential or URL', () async {
    final store = PreferencesUploadCheckpoints();
    final state = UploadCheckpoint(
      userId: 'alice',
      projectId: 'p',
      source: source,
      sessionId: 's',
      receivedBytes: 10,
      phase: UploadPhase.uncertain,
    );
    expect(await store.write(state), isA<Success<void>>());
    final restored = (await PreferencesUploadCheckpoints().read(
      'alice',
    ) as Success<UploadCheckpoint?>).value!;
    expect(restored.source.sameSource(source), isTrue);
    expect(restored.phase, UploadPhase.uncertain);
    expect(restored.projectId, 'p');
    expect(
      (await store.read('bob') as Success<UploadCheckpoint?>).value,
      isNull,
    );
    final text = (await SharedPreferences.getInstance()).getString(
      'model-upload.v1.alice',
    )!;
    final json = jsonDecode(text) as Map<String, dynamic>;
    expect(
      json.keys,
      containsAll(['source', 'projectId', 'sessionId', 'phase']),
    );
    expect(text, isNot(contains('Bearer')));
    expect(text, isNot(contains('http')));
  });
  test(
    'platform metadata is opaque and fingerprint mismatch rejects resume',
    () async {
      const channel = MethodChannel('test/platform-files');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'chooseModel') {
              return {
                'id': source.id,
                'fileName': source.fileName,
                'sizeBytes': 10,
                'fingerprint': 'abc',
              };
            }
            if (call.method == 'fingerprint') {
              return {'sizeBytes': 10, 'fingerprint': 'changed'};
            }
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      final adapter = AndroidLocalFileAdapter(channel: channel);
      final selected = (await adapter.choose(
        Cancellation(),
      ) as Success<LocalModelRef?>).value!;
      expect(selected.id, source.id);
      expect(selected.sameSource(source), isTrue);
      expect(
        (await adapter.verify(selected) as Failure).error.code,
        'source_changed',
      );
    },
  );
}
