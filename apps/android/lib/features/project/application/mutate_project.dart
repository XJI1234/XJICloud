import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/project.dart';
import '../domain/project_repository.dart';
import '../domain/workspace_persistence.dart';
import '../domain/recent_projects_policy.dart';

final class MutateProject {
  const MutateProject(this.repository, this.persistence);
  final ProjectRepository repository;
  final WorkspacePersistence persistence;
  Future<Result<Project>> save(
    String name,
    String description, {
    String? id,
  }) async {
    final trimmed = name.trim();
    if (trimmed.isEmpty || trimmed.length > 256 || description.length > 2000) {
      return const Failure(DomainError('project_validation'));
    }
    return id == null
        ? repository.create(trimmed, description.trim())
        : repository.update(id, trimmed, description.trim());
  }

  Future<Result<void>> delete(
    String userId,
    String id, {
    bool Function()? isCurrent,
  }) async {
    // Read first: a local storage error must not hide a completed remote delete.
    final stored = await persistence.read(userId);
    if (stored case Failure<WorkspaceSnapshot>(:final error)) {
      return Failure(error);
    }
    if (isCurrent?.call() == false) {
      return const Failure(DomainError('cancelled'));
    }
    final result = await repository.delete(id);
    if (result is Failure<void>) return result;
    if (isCurrent?.call() == false) {
      return const Failure(DomainError('cancelled'));
    }
    final written = await persistence.write(
      userId,
      RecentProjectsPolicy.remove(
        (stored as Success<WorkspaceSnapshot>).value,
        id,
      ),
    );
    // Distinct code lets the owner reconcile the successful remote deletion.
    if (written is Failure<void>) {
      return const Failure(DomainError('project_deleted_storage'));
    }
    return const Success(null);
  }
}
