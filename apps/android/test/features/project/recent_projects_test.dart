import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/project/domain/recent_projects_policy.dart';
import 'package:xjicloud_mobile/features/project/domain/workspace_persistence.dart';

void main() {
  test('opening deduplicates, moves to front and caps recents at five', () {
    var state = WorkspaceSnapshot();
    for (final id in ['a', 'b', 'c', 'd', 'e', 'f', 'c']) {
      state = RecentProjectsPolicy.open(state, id);
    }
    expect(state.activeProjectId, 'c');
    expect(state.recentIds, ['c', 'f', 'e', 'd', 'b']);
  });
  test('successful list prunes unavailable recents and active', () {
    final state = RecentProjectsPolicy.retain(
      WorkspaceSnapshot(activeProjectId: 'a', recentIds: ['a', 'b', 'b', 'c']),
      {'b'},
    );
    expect(state.activeProjectId, isNull);
    expect(state.recentIds, ['b']);
  });
}
