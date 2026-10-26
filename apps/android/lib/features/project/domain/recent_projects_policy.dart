import 'workspace_persistence.dart';

abstract final class RecentProjectsPolicy {
  static WorkspaceSnapshot open(WorkspaceSnapshot snapshot, String id) =>
      WorkspaceSnapshot(
        activeProjectId: id,
        recentIds: [
          id,
          ...snapshot.recentIds.where((value) => value != id),
        ].take(5),
      );
  static WorkspaceSnapshot retain(
    WorkspaceSnapshot snapshot,
    Set<String> ids,
  ) => WorkspaceSnapshot(
    activeProjectId: ids.contains(snapshot.activeProjectId)
        ? snapshot.activeProjectId
        : null,
    recentIds: snapshot.recentIds.where(ids.contains).toSet().take(5),
  );
  static WorkspaceSnapshot remove(WorkspaceSnapshot snapshot, String id) =>
      WorkspaceSnapshot(
        activeProjectId: snapshot.activeProjectId == id
            ? null
            : snapshot.activeProjectId,
        recentIds: snapshot.recentIds.where((value) => value != id),
      );
}
