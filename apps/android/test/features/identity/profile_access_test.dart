import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/completed_profile_access.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';
import '../project/project_fakes.dart';
import 'session_policy_test.dart';

void main() {
  test('pending profile does not restore workspace or query protected business APIs', () async {
    final coordinator = SessionCoordinator(MemoryStore());
    final access = CompletedProfileAccess(coordinator);
    final workspace = WorkspaceController(FakeProjects(), MemoryWorkspace(), access);
    addTearDown(coordinator.dispose); addTearDown(workspace.dispose);
    await coordinator.activate(UserSession(userId: 'alice', username: 'alice', displayName: 'Alice',
      token: 'secret', expiresAt: DateTime.utc(2030), profileRequired: true));
    workspace.synchronize();
    expect(access.session, isNull);
    expect(workspace.userId, isNull);
    expect(workspace.projects, isEmpty);
    await coordinator.activate(session());
    workspace.synchronize();
    await workspace.refresh();
    expect(workspace.projects, hasLength(2));
    expect(access.generation, coordinator.generation);
  });
}
