import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xjicloud_mobile/features/project/domain/workspace_persistence.dart';
import 'package:xjicloud_mobile/features/project/infrastructure/local_workspace_persistence.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));
  test('persisted active and five unique recents restore in another instance per account', () async {
    final store = LocalWorkspacePersistence();
    expect(
      await store.write(
        'alice',
        WorkspaceSnapshot(
          activeProjectId: 'a',
          recentIds: ['a', 'b', 'b', 'c', 'd', 'e', 'f'],
        ),
      ),
      isA<Success<void>>(),
    );
    await store.write(
      'bob',
      WorkspaceSnapshot(activeProjectId: 'x', recentIds: ['x']),
    );
    final reopened = LocalWorkspacePersistence();
    final alice = await reopened.read('alice') as Success<WorkspaceSnapshot>;
    expect(alice.value.activeProjectId, 'a');
    expect(alice.value.recentIds, ['a', 'b', 'c', 'd', 'e']);
    expect(
      (await reopened.read(
        'bob',
      ) as Success<WorkspaceSnapshot>).value.activeProjectId,
      'x',
    );
    expect(
      (await reopened.read(
        'other',
      ) as Success<WorkspaceSnapshot>).value.recentIds,
      isEmpty,
    );
  });
  test('corrupt saved state is an explicit failure', () async {
    SharedPreferences.setMockInitialValues({
      'workspace.v1.alice': 'broken-json',
    });
    expect(
      await LocalWorkspacePersistence().read('alice'),
      isA<Failure<WorkspaceSnapshot>>(),
    );
  });
}
