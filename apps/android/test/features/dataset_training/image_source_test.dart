import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/android_image_source_adapter.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/selected_image.dart';
import 'package:xjicloud_mobile/shared/infrastructure/platform_file_snapshots.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'multi-image and directory picker share bounded private snapshot bridge',
    () async {
      final root = await Directory.systemTemp.createTemp('xji-images-');
      addTearDown(() => root.delete(recursive: true));
      const id = '11111111-1111-1111-1111-111111111111';
      await File('${root.path}/$id').writeAsBytes([1, 2]);
      const channel = MethodChannel('test_image_snapshots');
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            return switch (call.method) {
              'chooseImages' => [
                {
                  'id': id,
                  'fileName': 'a.jpg',
                  'relativeName': 'dir/a.jpg',
                  'contentType': 'image/jpeg',
                  'sizeBytes': 2,
                  'fingerprint': 'hash',
                },
              ],
              'directories' => {'sources': root.path},
              'fingerprint' => {'sizeBytes': 2, 'fingerprint': 'hash'},
              _ => null,
            };
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      final sources = AndroidImageSourceAdapter(
        PlatformFileSnapshots(channel: channel),
      );
      final picked = (await sources.choose(
        false,
        Cancellation(),
      ) as Success<List<SelectedImage>?>).value!;
      expect(picked.single.relativeName, 'dir/a.jpg');
      expect(await sources.verify(picked.single), isA<Success<void>>());
      expect(await sources.read(picked.single).expand((x) => x).toList(), [
        1,
        2,
      ]);
      await sources.choose(true, Cancellation());
      expect(calls.where((c) => c.method == 'chooseImages').last.arguments, {
        'directory': true,
      });
      expect(await sources.release(picked.single), isA<Success<void>>());
      expect(await File('${root.path}/$id').exists(), isFalse);
    },
  );
}
