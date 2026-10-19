import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_policy.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/local_file_port.dart';
import 'package:xjicloud_mobile/features/model_asset/domain/model_revision.dart';

void main() {
  test(
    'accepts PLY SPZ and exact 2GiB; rejects empty unsupported traversal',
    () {
      for (final name in ['a.ply', 'B.SPZ']) {
        expect(ModelPolicy.validate(name, 2147483648), isNull);
      }
      for (final size in [0, -1, 2147483649]) {
        expect(ModelPolicy.validate('a.ply', size), isNotNull);
      }
      for (final name in ['a.zip', '../a.ply', 'a/b.ply', 'a\\b.ply']) {
        expect(ModelPolicy.validate(name, 4), isNotNull);
      }
    },
  );
  test('source equality requires bytes fingerprint size and name', () {
    const a = LocalModelRef(
      id: 'a',
      fileName: 'a.ply',
      sizeBytes: 4,
      fingerprint: 'sha1',
    );
    const b = LocalModelRef(
      id: 'b',
      fileName: 'a.ply',
      sizeBytes: 4,
      fingerprint: 'sha1',
    );
    expect(a.sameSource(b), isTrue);
    expect(
      a.sameSource(
        const LocalModelRef(
          id: 'a',
          fileName: 'a.ply',
          sizeBytes: 4,
          fingerprint: 'sha2',
        ),
      ),
      isFalse,
    );
  });
  test('revision identity includes user model time and size', () {
    final a = ModelRevision(
      userId: 'u',
      modelId: 'm',
      updatedAt: DateTime.utc(2026),
      sizeBytes: 4,
    );
    expect(
      a.key,
      ModelRevision(
        userId: 'u',
        modelId: 'm',
        updatedAt: DateTime.utc(2026),
        sizeBytes: 4,
      ).key,
    );
    expect(
      a.key,
      isNot(
        ModelRevision(
          userId: 'v',
          modelId: 'm',
          updatedAt: DateTime.utc(2026),
          sizeBytes: 4,
        ).key,
      ),
    );
    expect(
      a.key,
      isNot(
        ModelRevision(
          userId: 'u',
          modelId: 'm',
          updatedAt: DateTime.utc(2027),
          sizeBytes: 4,
        ).key,
      ),
    );
    expect(
      a.key,
      isNot(
        ModelRevision(
          userId: 'u',
          modelId: 'm',
          updatedAt: DateTime.utc(2026),
          sizeBytes: 5,
        ).key,
      ),
    );
  });
}
