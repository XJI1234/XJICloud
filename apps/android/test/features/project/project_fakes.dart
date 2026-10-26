import 'dart:async';

import 'package:xjicloud_mobile/features/project/domain/project.dart';
import 'package:xjicloud_mobile/features/project/domain/project_repository.dart';
import 'package:xjicloud_mobile/features/project/domain/workspace_persistence.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

Project project(String id, [String? name]) => Project(
  id: id,
  name: name ?? 'Project $id',
  description: 'Description $id',
  createdAt: DateTime.utc(2026, 10, 20),
);

class MemoryWorkspace implements WorkspacePersistence {
  final values = <String, WorkspaceSnapshot>{};
  bool fail = false;
  @override
  Future<Result<WorkspaceSnapshot>> read(String userId) async =>
      Success(values[userId] ?? WorkspaceSnapshot());
  @override
  Future<Result<void>> write(String userId, WorkspaceSnapshot snapshot) async {
    if (fail) return const Failure(DomainError('preferences'));
    values[userId] = snapshot;
    return const Success(null);
  }
}

class FakeProjects implements ProjectRepository {
  List<Project> projects = [project('a'), project('b')];
  bool fail = false;
  Completer<Result<List<Project>>>? gate;
  @override
  Future<Result<List<Project>>> list() async => gate != null
      ? gate!.future
      : fail
      ? const Failure(DomainError('network'))
      : Success(List.of(projects));
  @override
  Future<Result<Project>> create(String name, String description) async {
    if (fail) return const Failure(DomainError('network'));
    final p = Project(
      id: 'new',
      name: name,
      description: description,
      createdAt: DateTime.utc(2026),
    );
    projects.add(p);
    return Success(p);
  }

  @override
  Future<Result<Project>> update(
    String id,
    String name,
    String description,
  ) async {
    if (fail) return const Failure(DomainError('forbidden'));
    final p = Project(
      id: id,
      name: name,
      description: description,
      createdAt: DateTime.utc(2026),
    );
    projects = projects.map((old) => old.id == id ? p : old).toList();
    return Success(p);
  }

  @override
  Future<Result<void>> delete(String id) async {
    if (fail) return const Failure(DomainError('forbidden'));
    projects.removeWhere((p) => p.id == id);
    return const Success(null);
  }
}
