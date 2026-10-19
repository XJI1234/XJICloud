import '../../../shared/domain/result.dart';
import 'project.dart';

abstract interface class ProjectRepository {
  Future<Result<List<Project>>> list();
  Future<Result<Project>> create(String name, String description);
  Future<Result<Project>> update(String id, String name, String description);
  Future<Result<void>> delete(String id);
}
