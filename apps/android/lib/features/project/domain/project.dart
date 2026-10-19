final class Project {
  const Project({
    required this.id,
    required this.name,
    required this.description,
    required this.createdAt,
  });
  final String id, name, description;
  final DateTime createdAt;
}
