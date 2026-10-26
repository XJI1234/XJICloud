import '../../../shared/domain/result.dart';

final class WorkspaceSnapshot {
  WorkspaceSnapshot({
    this.activeProjectId,
    Iterable<String> recentIds = const [],
  }) : recentIds = List.unmodifiable(recentIds);
  final String? activeProjectId;
  final List<String> recentIds;
}

abstract interface class WorkspacePersistence {
  Future<Result<WorkspaceSnapshot>> read(String userId);
  Future<Result<void>> write(String userId, WorkspaceSnapshot snapshot);
}
