import '../domain/project.dart';

abstract final class ProjectMapper {
  static final idPattern = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  static Project fromJson(Object? value) {
    if (value is! Map<String, dynamic> ||
        value['id'] is! String ||
        !idPattern.hasMatch(value['id'] as String) ||
        value['name'] is! String ||
        (value['description'] != null && value['description'] is! String) ||
        value['createdAt'] is! String) {
      throw const FormatException('project');
    }
    final date = DateTime.tryParse(value['createdAt'] as String);
    if (date == null) throw const FormatException('project time');
    return Project(
      id: value['id'] as String,
      name: value['name'] as String,
      description: value['description'] as String? ?? '',
      createdAt: date,
    );
  }
}
