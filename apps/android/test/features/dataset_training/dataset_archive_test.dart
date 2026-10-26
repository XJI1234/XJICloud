import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/application/prepare_dataset.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/selected_image.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/dataset_manifest.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

SelectedImage image(
  int i, {
  String? name,
  String? relative,
  String mime = 'image/jpeg',
  int size = 1,
}) => SelectedImage(
  sourceId: '$i',
  name: name ?? '$i.jpg',
  relativeName: relative ?? '$i.jpg',
  contentType: mime,
  sizeBytes: size,
  fingerprint: 'hash$i',
);
void main() {
  test(
    'archive accepts one and 9999 images but rejects four-digit overflow',
    () {
      expect(prepareDataset([image(0)]), isA<Success<DatasetManifest>>());
      expect(
        prepareDataset(List.generate(9999, image)),
        isA<Success<DatasetManifest>>(),
      );
      expect(
        prepareDataset(List.generate(10000, image)),
        isA<Failure<DatasetManifest>>(),
      );
    },
  );
  test('empty, zero bytes, HEIC and contradictory MIME are rejected', () {
    for (final files in <List<SelectedImage>>[
      [],
      [image(1, size: 0)],
      [image(1, name: 'a.heic', mime: 'image/heic')],
      [image(1, mime: 'image/png')],
    ]) {
      expect(prepareDataset(files), isA<Failure<DatasetManifest>>());
    }
  });
  test('stable relative-name order, source association and manifest v1', () {
    final a = image(2, name: 'same.jpg', relative: 'b/same.jpg');
    final b = image(1, name: 'same.jpg', relative: 'a/same.jpg');
    final manifest = (prepareDataset([a, b]) as Success<DatasetManifest>).value;
    expect(manifest.files.map((f) => f.source.sourceId), ['1', '2']);
    expect(manifest.files.map((f) => f.archivedName), ['0001.jpg', '0002.jpg']);
    final data = jsonDecode(utf8.decode(manifest.bytes)) as Map;
    expect(data['version'], 1);
    expect(data['imageCount'], 2);
    expect((data['files'] as List).first, {
      'archivedName': '0001.jpg',
      'originalName': 'same.jpg',
      'contentType': 'image/jpeg',
      'sizeBytes': 1,
    });
    expect(utf8.decode(manifest.bytes), isNot(contains('hash')));
    expect(manifest.totalBytes, 2 + manifest.bytes.length);
  });
  test(
    'equal relative names use stable identity and reject duplicate sources',
    () {
      final a = image(2, name: 'a.jpg', relative: 'a.jpg'),
          b = image(1, name: 'a.jpg', relative: 'a.jpg');
      expect(
        (prepareDataset([
          a,
          b,
        ]) as Success<DatasetManifest>).value.files.first.source.sourceId,
        '1',
      );
      expect(prepareDataset([a, a]), isA<Failure<DatasetManifest>>());
    },
  );
}
