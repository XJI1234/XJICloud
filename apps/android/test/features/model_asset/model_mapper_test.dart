import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/model_asset/infrastructure/model_mapper.dart';

void main() {
  final dto = <String, dynamic>{
    'id': '12345678-1234-1234-1234-123456789abc',
    'projectId': '12345678-1234-1234-1234-123456789abd',
    'fileName': 'a.ply',
    'format': 'PLY',
    'sizeBytes': 4,
    'version': 2,
    'createdAt': '2026-01-01T00:00:00Z',
    'updatedAt': '2026-01-02T00:00:00Z',
  };
  test(
    'maps exact backend ModelResponse revision and rejects malformed identity',
    () {
      final m = ModelMapper.fromJson(dto);
      expect(m.version, 2);
      expect(m.updatedAt, DateTime.utc(2026, 1, 2));
      for (final patch in [
        {'sizeBytes': -1},
        {'format': 'ZIP'},
        {'id': '../x'},
        {'updatedAt': 'broken'},
      ]) {
        expect(
          () => ModelMapper.fromJson({...dto, ...patch}),
          throwsFormatException,
        );
      }
    },
  );
}
