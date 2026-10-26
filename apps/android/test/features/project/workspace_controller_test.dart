import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/project/presentation/workspace_controller.dart';
import 'package:xjicloud_mobile/features/project/domain/project.dart';
import 'package:xjicloud_mobile/features/project/domain/workspace_persistence.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'project_fakes.dart';

void main() {
  test(
    'late list after account change cannot publish or prune another account',
    () async {
      final sessions = SessionCoordinator(MemoryStore());
      final repo = FakeProjects()..gate = Completer<Result<List<Project>>>();
      final store = MemoryWorkspace();
      final c = WorkspaceController(repo, store, sessions);
      addTearDown(c.dispose);
      addTearDown(sessions.dispose);
      await sessions.activate(session());
      c.synchronize();
      final pending = c.refresh();
      await Future<void>.delayed(Duration.zero);
      await sessions.invalidate();
      c.synchronize();
      repo.gate!.complete(Success([project('a')]));
      await pending;
      expect(c.projects, isEmpty);
      expect(c.activeProjectId, isNull);
      expect(c.userId, isNull);
      expect(store.values, isEmpty);
    },
  );
  test('failed refresh retains visible content and recents, successful empty prunes', () async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    final repo = FakeProjects();
    final store = MemoryWorkspace()
      ..values[sessions.session!.userId] = WorkspaceSnapshot(
        activeProjectId: 'a',
        recentIds: ['a', 'missing'],
      );
    final c = WorkspaceController(repo, store, sessions);
    addTearDown(c.dispose);
    addTearDown(sessions.dispose);
    c.synchronize();
    await c.refresh();
    expect(c.home.current!.id, 'a');
    expect(c.recentProjects.map((p) => p.id), ['a']);
    repo.fail = true;
    await c.refresh();
    expect(c.projects, hasLength(2));
    expect(c.error!.code, 'network');
    expect(c.activeProjectId, 'a');
    repo.fail = false;
    repo.projects = [];
    await c.refresh();
    expect(c.projects, isEmpty);
    expect(c.activeProjectId, isNull);
    expect(c.error, isNull);
  });
  test('remote deletion with persistence failure still clears memory and reports reconciliation', () async {
    final sessions = SessionCoordinator(MemoryStore());
    await sessions.activate(session());
    final store = MemoryWorkspace()
      ..values['alice'] = WorkspaceSnapshot(
        activeProjectId: 'a',
        recentIds: ['a'],
      );
    final c = WorkspaceController(FakeProjects(), store, sessions);
    addTearDown(c.dispose);
    addTearDown(sessions.dispose);
    c.synchronize();
    await c.refresh();
    store.fail = true;
    expect(await c.deleteProject('a'), isTrue);
    expect(c.projectById('a'), isNull);
    expect(c.activeProjectId, isNull);
    expect(c.error!.code, 'project_deleted_storage');
  });
}
