import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/file_training_outputs.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_output_port.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';

void main() {
  test('viewer lease defers logout removal and denies reacquisition', () async {
    final root = await Directory.systemTemp.createTemp('xji-output-lease-');
    addTearDown(() => root.delete(recursive: true));
    final store = FileTrainingOutputs(() async => root.path, (
      url,
      c,
      consume,
    ) async {
      await consume(Stream.value([1, 2, 3]), 3);
      return const Success(null);
    });
    final ref = (await store.download(
      'u',
      'j',
      'https://oss.test/o',
      Cancellation(),
      (_, _) {},
    ) as Success<TrainingOutputRef>).value;
    final lease = await store.acquire(ref, 'u');
    expect(await store.remove(ref), isA<Success<void>>());
    expect(await File(lease.path).exists(), isTrue);
    await expectLater(store.acquire(ref, 'u'), throwsA(isA<DomainError>()));
    await lease.release();
    expect(await File(lease.path).exists(), isFalse);
    await lease.release();
  });
  test(
    'cold output store reclaims old published and interrupted files once',
    () async {
      final root = await Directory.systemTemp.createTemp('xji-output-restart-');
      addTearDown(() => root.delete(recursive: true));
      FileTrainingOutputs store() =>
          FileTrainingOutputs(() async => root.path, (url, c, consume) async {
            await consume(Stream.value([1, 2, 3]), 3);
            return const Success(null);
          });
      final previous = store();
      final old = (await previous.download(
        'u',
        'old',
        'https://oss.test/o',
        Cancellation(),
        (_, _) {},
      ) as Success<TrainingOutputRef>).value;
      final oldPath = await previous.resolve(old, 'u');
      final part = File('$oldPath.part');
      await part.writeAsBytes([1]);
      final current = store();
      final fresh = (await current.download(
        'u',
        'new',
        'https://oss.test/o',
        Cancellation(),
        (_, _) {},
      ) as Success<TrainingOutputRef>).value;
      expect(await File(oldPath).exists(), isFalse);
      expect(await part.exists(), isFalse);
      await current.download(
        'other',
        'next',
        'https://oss.test/o',
        Cancellation(),
        (_, _) {},
      );
      expect(await File(await current.resolve(fresh, 'u')).exists(), isTrue);
    },
  );
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
