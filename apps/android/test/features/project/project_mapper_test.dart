import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/project/infrastructure/project_mapper.dart';

void main() {
  final fixture = <String, Object?>{
    'id': '3d1fa7eb-3d29-404b-b6c9-274f77e52de5',
    'name': 'Site',
    'description': null,
    'createdAt': '2026-10-20T01:02:03Z',
  };
  test('maps actual ProjectResponse including nullable description', () {
    final p = ProjectMapper.fromJson(fixture);
    expect(p.id, fixture['id']);
    expect(p.name, 'Site');
    expect(p.description, '');
    expect(p.createdAt, DateTime.utc(2026, 10, 20, 1, 2, 3));
  });
  test('rejects malformed id, time and missing name', () {
    for (final change in [
      {'id': '../admin'},
      {'createdAt': 'not-time'},
      {'name': null},
    ]) {
      expect(
        () => ProjectMapper.fromJson({...fixture, ...change}),
        throwsFormatException,
      );
    }
  });
}
