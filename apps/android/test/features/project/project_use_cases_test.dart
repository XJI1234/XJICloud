import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/project/application/open_project.dart';
import 'package:xjicloud_mobile/features/project/application/load_projects.dart';
import 'package:xjicloud_mobile/features/project/application/mutate_project.dart';
import 'package:xjicloud_mobile/features/project/domain/project.dart';
import 'package:xjicloud_mobile/features/project/domain/workspace_persistence.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'project_fakes.dart';

void main() {
  test(
    'open verifies owned list before writing and accounts stay isolated',
    () async {
      final repo = FakeProjects();
      final store = MemoryWorkspace();
      final open = OpenProject(repo, store);
      expect(await open('alice', 'a'), isA<Success<WorkspaceSnapshot>>());
      expect(await open('bob', 'b'), isA<Success<WorkspaceSnapshot>>());
      expect(store.values['alice']!.activeProjectId, 'a');
      expect(store.values['bob']!.recentIds, ['b']);
      expect(
        await open('bob', 'someone-elses-project'),
        isA<Failure<WorkspaceSnapshot>>(),
      );
      expect(store.values['bob']!.activeProjectId, 'b');
    },
  );
  test(
    'delete current cleans active and recents only after backend success',
    () async {
      final repo = FakeProjects();
      final store = MemoryWorkspace()
        ..values['alice'] = WorkspaceSnapshot(
          activeProjectId: 'a',
          recentIds: ['a', 'b'],
        );
      final mutate = MutateProject(repo, store);
      repo.fail = true;
      expect(await mutate.delete('alice', 'a'), isA<Failure<void>>());
      expect(store.values['alice']!.activeProjectId, 'a');
      repo.fail = false;
      expect(await mutate.delete('alice', 'a'), isA<Success<void>>());
      expect(store.values['alice']!.activeProjectId, isNull);
      expect(store.values['alice']!.recentIds, ['b']);
    },
  );
  test('empty success differs from offline failure', () async {
    final repo = FakeProjects()..projects = [];
    expect(
      (await LoadProjects(repo)() as Success<List<Project>>).value,
      isEmpty,
    );
    repo.fail = true;
    expect(await LoadProjects(repo)(), isA<Failure<List<Project>>>());
  });
  test('save validates limits and trims before create/update', () async {
    final repo = FakeProjects();
    final mutate = MutateProject(repo, MemoryWorkspace());
    expect(await mutate.save(' ', ''), isA<Failure<Project>>());
    expect(await mutate.save('a' * 257, ''), isA<Failure<Project>>());
    expect(await mutate.save('name', 'a' * 2001), isA<Failure<Project>>());
    final created = await mutate.save(' New ', ' detail ') as Success<Project>;
    expect(created.value.name, 'New');
    expect(created.value.description, 'detail');
    expect(
      (await mutate.save('Changed', '', id: 'a') as Success<Project>).value.id,
      'a',
    );
  });
  test('account change while verifying open never writes old active', () async {
    final repo = FakeProjects();
    final store = MemoryWorkspace();
    expect(
      await OpenProject(repo, store)('alice', 'a', isCurrent: () => false),
      isA<Failure<WorkspaceSnapshot>>(),
    );
    expect(store.values, isEmpty);
  });
  test(
    'session changing during local read never deletes under a new identity',
    () async {
      final repo = FakeProjects();
      final result = await MutateProject(
        repo,
        MemoryWorkspace(),
      ).delete('alice', 'a', isCurrent: () => false);
      expect(repo.projects.map((p) => p.id), contains('a'));
      expect(result, isA<Failure<void>>());
    },
  );
}
