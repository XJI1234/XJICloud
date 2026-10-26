import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/dataset_archive_policy.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/dataset_manifest.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/selected_image.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/upload_checkpoint_port.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/local_upload_checkpoint.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/android_image_source_adapter.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/upload_checkpoint_port.dart'
    as model;
import 'package:xjicloud_mobile/features/model_asset/infrastructure/preferences_upload_checkpoints.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/android_local_file_adapter.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/infrastructure/platform_file_snapshots.dart';
import 'package:xjicloud_mobile/shared/infrastructure/reconcile_private_sources.dart';

const first = '11111111-1111-1111-1111-111111111111';
const second = '22222222-2222-2222-2222-222222222222';
const orphan = '33333333-3333-3333-3333-333333333333';
const modelSource = LocalModelRef(
  id: second,
  fileName: 'a.ply',
  sizeBytes: 1,
  fingerprint: 'h',
);
const imageSource = SelectedImage(
  sourceId: first,
  name: 'a.jpg',
  relativeName: 'a.jpg',
  contentType: 'image/jpeg',
  sizeBytes: 1,
  fingerprint: 'h',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('restart reclaims preview snapshots and parts preserving every account checkpoint', () async {
    SharedPreferences.setMockInitialValues({});
    final root = await Directory.systemTemp.createTemp('xji-sources-restart-');
    addTearDown(() => root.delete(recursive: true));
    final sourceDir = await Directory('${root.path}/sources').create();
    for (final id in [first, second, orphan, '$orphan.part']) {
      await File('${sourceDir.path}/$id').writeAsBytes([1]);
    }
    final datasets = LocalUploadCheckpoint(
      () async => '${root.path}/checkpoints',
    );
    final models = PreferencesUploadCheckpoints();
    final manifest =
        (archiveImages([imageSource]) as Success<DatasetManifest>).value;
    expect(
      await datasets.write(
        DatasetCheckpoint(
          userId: 'account-A',
          projectId: 'p',
          name: 'n',
          phase: DatasetUploadPhase.creating,
          manifest: manifest,
        ),
      ),
      isA<Success<void>>(),
    );
    expect(
      await models.write(
        const model.UploadCheckpoint(
          userId: 'account-B',
          projectId: 'p',
          source: modelSource,
          sessionId: 's',
        ),
      ),
      isA<Success<void>>(),
    );
    final snapshots = PlatformFileSnapshots()
      ..initialize = () => reconcilePrivateSources(sourceDir.path, [
        models.referencedSources,
        datasets.referencedSources,
      ]);
    await snapshots.ready();
    expect(await File('${sourceDir.path}/$first').exists(), isTrue);
    expect(await File('${sourceDir.path}/$second').exists(), isTrue);
    expect(await File('${sourceDir.path}/$orphan').exists(), isFalse);
    expect(await File('${sourceDir.path}/$orphan.part').exists(), isFalse);
    // A selected source or viewer lease created after startup survives all reuse.
    await File('${sourceDir.path}/$orphan').writeAsBytes([2]);
    await snapshots.ready();
    expect(await File('${sourceDir.path}/$orphan').exists(), isTrue);
  });

  test('unreadable model or dataset inventory fails closed before deleting any source', () async {
    final root = await Directory.systemTemp.createTemp('xji-sources-closed-');
    addTearDown(() => root.delete(recursive: true));
    final source = File('${root.path}/$orphan');
    await source.writeAsBytes([1]);
    SharedPreferences.setMockInitialValues({
      'model-upload.v1.other-account': '{broken',
    });
    await expectLater(
      reconcilePrivateSources(root.path, [
        PreferencesUploadCheckpoints().referencedSources,
      ]),
      throwsA(isA<DomainError>()),
    );
    expect(await source.exists(), isTrue);
    final checkpoints = await Directory('${root.path}/checkpoints').create();
    await File('${checkpoints.path}/broken.state.json').writeAsString('{}');
    await expectLater(
      reconcilePrivateSources(root.path, [
        LocalUploadCheckpoint(() async => checkpoints.path).referencedSources,
      ]),
      throwsA(isA<DomainError>()),
    );
    expect(await source.exists(), isTrue);
  });

  test('startup barrier gates new pickers and cold image/model verify read resolve', () async {
    final root = await Directory.systemTemp.createTemp('xji-source-barrier-');
    addTearDown(() => root.delete(recursive: true));
    for (final id in [first, second]) {
      await File('${root.path}/$id').writeAsBytes([1]);
    }
    const channel = MethodChannel('private-file-barrier');
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call.method);
          return switch (call.method) {
            'directories' => {'sources': root.path},
            'fingerprint' => {'sizeBytes': 1, 'fingerprint': 'h'},
            _ => null,
          };
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );
    final barrier = Completer<void>();
    final snapshots = PlatformFileSnapshots(channel: channel)
      ..initialize = () => barrier.future;
    final images = AndroidImageSourceAdapter(snapshots);
    final models = AndroidLocalFileAdapter(snapshots: snapshots);
    var finished = 0;
    final operations =
        <Future<Object?>>[
              images.choose(false, Cancellation()),
              models.choose(Cancellation()),
              images.verify(imageSource),
              models.verify(modelSource),
              images.read(imageSource).toList(),
              models.read(modelSource, 0, 1).toList(),
              models.resolveSource(modelSource),
            ]
            .map(
              (f) => f.then((v) {
                finished++;
                return v;
              }),
            )
            .toList();
    await Future<void>.delayed(Duration.zero);
    expect(calls, isEmpty);
    expect(finished, 0);
    barrier.complete();
    await Future.wait(operations);
    expect(finished, 7);
    expect(calls, containsAll(['chooseImages', 'chooseModel', 'fingerprint']));
  });

  test('failed startup maps to storage error and never launches SAF', () async {
    final snapshots = PlatformFileSnapshots()
      ..initialize = () async {
        throw const DomainError('protocol');
      };
    expect(
      (await AndroidImageSourceAdapter(
        snapshots,
      ).choose(false, Cancellation()) as Failure).error.code,
      'storage',
    );
    expect(
      (await AndroidLocalFileAdapter(
        snapshots: snapshots,
      ).verify(modelSource) as Failure).error.code,
      'storage',
    );
  });
}
