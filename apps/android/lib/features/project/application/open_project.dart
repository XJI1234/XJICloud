import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/project_repository.dart';
import '../domain/workspace_persistence.dart';
import '../domain/project.dart';
import '../domain/recent_projects_policy.dart';

final class OpenProject {
  const OpenProject(this.repository, this.persistence);
  final ProjectRepository repository;
  final WorkspacePersistence persistence;
  Future<Result<WorkspaceSnapshot>> call(
    String userId,
    String id, {
    bool Function()? isCurrent,
  }) async {
    final listed = await repository.list();
    if (listed case Failure<List<Project>>(:final error)) return Failure(error);
    if (isCurrent?.call() == false) {
      return const Failure(DomainError('cancelled'));
    }
    final ids = (listed as Success<List<Project>>).value
        .map((p) => p.id)
        .toSet();
    if (!ids.contains(id)) {
      return const Failure(DomainError('project_unavailable'));
    }
    final stored = await persistence.read(userId);
    if (stored case Failure<WorkspaceSnapshot>(:final error)) {
      return Failure(error);
    }
    if (isCurrent?.call() == false) {
      return const Failure(DomainError('cancelled'));
    }
    final next = RecentProjectsPolicy.open(
      RecentProjectsPolicy.retain(
        (stored as Success<WorkspaceSnapshot>).value,
        ids,
      ),
      id,
    );
    final written = await persistence.write(userId, next);
    if (written case Failure<void>(:final error)) return Failure(error);
    return Success(next);
  }
}
