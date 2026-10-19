import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/workspace_persistence.dart';

final class LocalWorkspacePersistence implements WorkspacePersistence {
  String _key(String userId) => 'workspace.v1.${Uri.encodeComponent(userId)}';
  @override
  Future<Result<WorkspaceSnapshot>> read(String userId) async {
    try {
      final raw = (await SharedPreferences.getInstance()).getString(
        _key(userId),
      );
      if (raw == null) return Success(WorkspaceSnapshot());
      final data = jsonDecode(raw) as Map<String, dynamic>;
      final active = data['activeProjectId'] as String?;
      final recent = (data['recentIds'] as List).cast<String>().toSet().take(5);
      return Success(
        WorkspaceSnapshot(activeProjectId: active, recentIds: recent),
      );
    } on Object {
      return const Failure(DomainError('workspace_storage'));
    }
  }

  @override
  Future<Result<void>> write(String userId, WorkspaceSnapshot snapshot) async {
    try {
      final saved = await (await SharedPreferences.getInstance()).setString(
        _key(userId),
        jsonEncode({
          'activeProjectId': snapshot.activeProjectId,
          'recentIds': snapshot.recentIds.toSet().take(5).toList(),
        }),
      );
      return saved
          ? const Success(null)
          : const Failure(DomainError('workspace_storage'));
    } on Object {
      return const Failure(DomainError('workspace_storage'));
    }
  }
}
