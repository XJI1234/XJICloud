import 'package:flutter/foundation.dart';

import '../../identity/domain/session_access.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/project.dart';
import '../domain/project_repository.dart';
import '../domain/workspace_persistence.dart';
import '../domain/recent_projects_policy.dart';
import '../application/load_projects.dart';
import '../application/open_project.dart';
import '../application/mutate_project.dart';

final class HomeViewModel {
  HomeViewModel({
    required this.current,
    required List<Project> recent,
    required this.loading,
    required this.error,
    required this.hasProjects,
  }) : recent = List.unmodifiable(recent);
  final Project? current;
  final List<Project> recent;
  final bool loading, hasProjects;
  final DomainError? error;
}

final class WorkspaceController extends ChangeNotifier {
  WorkspaceController(this.repository, this.persistence, this.sessions);
  final ProjectRepository repository;
  final WorkspacePersistence persistence;
  final SessionAccess sessions;
  String? userId;
  int userGeneration = -1;
  List<Project> projects = const [];
  WorkspaceSnapshot snapshot = WorkspaceSnapshot();
  String? selectedProjectId;
  bool loading = false, busy = false;
  DomainError? error;
  bool _disposed = false;
  Future<void> _queue = Future.value();
  bool _current(int generation) =>
      !_disposed &&
      generation == userGeneration &&
      generation == sessions.generation &&
      userId == sessions.session?.userId;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<T> _serial<T>(Future<T> Function() operation) {
    final next = _queue.then((_) => operation());
    _queue = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  String? get activeProjectId => snapshot.activeProjectId;
  List<Project> get recentProjects => snapshot.recentIds
      .map(projectById)
      .whereType<Project>()
      .toList(growable: false);
  Project? projectById(String? id) =>
      projects.where((p) => p.id == id).firstOrNull;
  HomeViewModel get home => HomeViewModel(
    current: projectById(activeProjectId),
    recent: recentProjects,
    loading: loading,
    error: error,
    hasProjects: projects.isNotEmpty,
  );
  void synchronize() {
    if (userGeneration == sessions.generation &&
        userId == sessions.session?.userId) {
      return;
    }
    userGeneration = sessions.generation;
    userId = sessions.session?.userId;
    projects = const [];
    snapshot = WorkspaceSnapshot();
    selectedProjectId = null;
    loading = busy = false;
    error = null;
    _notify();
    if (userId != null) refresh();
  }

  Future<void> refresh() {
    final generation = userGeneration;
    final account = userId;
    if (account == null) return Future.value();
    return _serial(() async {
      if (!_current(generation)) return;
      loading = true;
      error = null;
      _notify();
      final stored = await persistence.read(account);
      if (!_current(generation)) return;
      final result = await LoadProjects(repository)();
      if (!_current(generation)) return;
      if (result case Success<List<Project>>(:final value)) {
        projects = List.unmodifiable(value);
        if (selectedProjectId != null &&
            projectById(selectedProjectId) == null) {
          selectedProjectId = null;
        }
        if (stored case Success<WorkspaceSnapshot>(value: final state)) {
          snapshot = RecentProjectsPolicy.retain(
            state,
            value.map((p) => p.id).toSet(),
          );
          final saved = await persistence.write(account, snapshot);
          if (!_current(generation)) return;
          if (saved case Failure<void>(error: final saveError)) {
            error = saveError;
          }
        } else if (stored case Failure<WorkspaceSnapshot>(:final error)) {
          this.error = error;
        }
      } else if (result case Failure<List<Project>>(:final error)) {
        this.error = error;
      }
      loading = false;
      _notify();
    });
  }

  void select(String id) {
    selectedProjectId = id;
    _notify();
  }

  Future<bool> _mutate(
    Future<bool> Function(int generation, String account) operation,
  ) {
    if (busy || userId == null) return Future.value(false);
    final generation = userGeneration;
    final account = userId!;
    busy = true;
    error = null;
    _notify();
    return _serial(() async {
      if (!_current(generation)) return false;
      final ok = await operation(generation, account);
      if (!_current(generation)) return false;
      busy = false;
      _notify();
      return ok;
    });
  }

  Future<bool> openProject(String id) => _mutate((generation, account) async {
    final result = await OpenProject(repository, persistence)(
      account,
      id,
      isCurrent: () => _current(generation),
    );
    if (!_current(generation)) return false;
    switch (result) {
      case Success<WorkspaceSnapshot>(:final value):
        snapshot = value;
        return true;
      case Failure<WorkspaceSnapshot>(:final error):
        this.error = error;
        return false;
    }
  });
  Future<bool> save(String name, String description, {String? id}) =>
      _mutate((generation, account) async {
        final result = await MutateProject(
          repository,
          persistence,
        ).save(name, description, id: id);
        if (!_current(generation)) return false;
        switch (result) {
          case Success<Project>(:final value):
            projects = List.unmodifiable([
              if (id == null) value,
              ...projects.map((p) => p.id == value.id ? value : p),
            ]);
            return true;
          case Failure<Project>(:final error):
            this.error = error;
            return false;
        }
      });
  Future<bool> deleteProject(String id) => _mutate((generation, account) async {
    final result = await MutateProject(
      repository,
      persistence,
    ).delete(account, id, isCurrent: () => _current(generation));
    if (!_current(generation)) return false;
    final remoteDeleted =
        result is Success<void> ||
        result is Failure<void> &&
            result.error.code == 'project_deleted_storage';
    if (remoteDeleted) {
      projects = List.unmodifiable(projects.where((p) => p.id != id));
      snapshot = RecentProjectsPolicy.remove(snapshot, id);
      if (selectedProjectId == id) selectedProjectId = null;
    }
    if (result case Failure<void>(error: final deleteError)) {
      error = deleteError;
    }
    return remoteDeleted;
  });
  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
