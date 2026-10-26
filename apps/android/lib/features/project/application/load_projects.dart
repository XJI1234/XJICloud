import '../../../shared/domain/result.dart';
import '../domain/project.dart';
import '../domain/project_repository.dart';

final class LoadProjects {
  const LoadProjects(this.repository);
  final ProjectRepository repository;
  Future<Result<List<Project>>> call() => repository.list();
}
