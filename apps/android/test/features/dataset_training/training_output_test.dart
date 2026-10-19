import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/file_training_outputs.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_output_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';

void main() {
  test(
    'private output publishes only after bounded download and isolates account',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'xji-training-output-',
      );
      addTearDown(() => root.delete(recursive: true));
      final store = FileTrainingOutputs(() => Future.value(root.path), (
        url,
        c,
        consume,
      ) async {
        await consume(Stream.value([1, 2, 3]), 3);
        return const Success(null);
      });
      final result = await store.download(
        'u',
        'j',
        'https://oss.test/output',
        Cancellation(),
        (_, _) {},
      );
      expect(result, isA<Success<TrainingOutputRef>>());
      final ref = (result as Success<TrainingOutputRef>).value;
      expect(ref.sizeBytes, 3);
      expect(await File(await store.resolve(ref, 'u')).readAsBytes(), [
        1,
        2,
        3,
      ]);
      await expectLater(
        store.resolve(ref, 'other'),
        throwsA(isA<DomainError>()),
      );
    },
  );
  test('failed, truncated or cancelled download leaves no partial/published output', () async {
    final root = await Directory.systemTemp.createTemp('xji-training-fail-');
    addTearDown(() => root.delete(recursive: true));
    final store = FileTrainingOutputs(() => Future.value(root.path), (
      url,
      c,
      consume,
    ) async {
      await consume(Stream.value([1, 2]), 3);
      return const Success(null);
    });
    expect(
      await store.download(
        'u',
        'j',
        'https://oss.test/output',
        Cancellation(),
        (_, _) {},
      ),
      isA<Failure<TrainingOutputRef>>(),
    );
    expect(await root.list(recursive: true).where((e) => e is File).length, 0);
  });
}
