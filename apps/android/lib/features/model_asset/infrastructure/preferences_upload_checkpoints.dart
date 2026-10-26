import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../domain/local_file_port.dart';
import '../domain/upload_checkpoint_port.dart';
import 'model_mapper.dart';

final class PreferencesUploadCheckpoints implements UploadCheckpointPort {
  /// Inventory every account, independently of the currently signed-in user.
  Future<Result<Set<String>>> referencedSources() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      final retained = <String>{};
      for (final key in preferences.getKeys().where(
        (k) => k.startsWith('model-upload.v1.'),
      )) {
        final user = Uri.decodeComponent(
          key.substring('model-upload.v1.'.length),
        );
        switch (await read(user)) {
          case Success<UploadCheckpoint?>(:final value):
            if (value == null) {
              throw const FormatException('missing checkpoint');
            }
            if (!RegExp(r'^[a-f0-9-]{36}$').hasMatch(value.source.id)) {
              throw const FormatException('source identity');
            }
            retained.add(value.source.id);
          case Failure<UploadCheckpoint?>(:final error):
            return Failure(error);
        }
      }
      return Success(retained);
    } catch (_) {
      return const Failure(DomainError('storage'));
    }
  }

  Future<void> _tail = Future.value();
  String _key(String userId) =>
      'model-upload.v1.${Uri.encodeComponent(userId)}';
  Future<Result<T>> _serial<T>(Future<T> Function() operation) {
    final result = _tail.then((_) async {
      try {
        return Success<T>(await operation());
      } catch (_) {
        return Failure<T>(const DomainError('storage'));
      }
    });
    _tail = result.then<void>((_) {});
    return result;
  }

  @override
  Future<Result<UploadCheckpoint?>> read(String userId) => _serial(() async {
    final preferences = await SharedPreferences.getInstance();
    final text = preferences.getString(_key(userId));
    if (text == null) return null;
    final data = jsonDecode(text) as Map<String, dynamic>;
    if (data['userId'] != userId) throw const FormatException('owner');
    final source = data['source'] as Map<String, dynamic>;
    return UploadCheckpoint(
      userId: userId,
      projectId: data['projectId'] as String,
      source: LocalModelRef(
        id: source['id'] as String,
        fileName: source['fileName'] as String,
        sizeBytes: source['sizeBytes'] as int,
        fingerprint: source['fingerprint'] as String,
      ),
      sessionId: data['sessionId'] as String,
      receivedBytes: data['receivedBytes'] as int,
      phase: UploadPhase.values.byName(data['phase'] as String),
      model: data['model'] == null ? null : ModelMapper.fromJson(data['model']),
    );
  });
  @override
  Future<Result<void>> write(UploadCheckpoint state) => _serial(() async {
    final preferences = await SharedPreferences.getInstance();
    final saved = await preferences.setString(
      _key(state.userId),
      jsonEncode({
        'userId': state.userId,
        'projectId': state.projectId,
        'sessionId': state.sessionId,
        'receivedBytes': state.receivedBytes,
        'phase': state.phase.name,
        'source': {
          'id': state.source.id,
          'fileName': state.source.fileName,
          'sizeBytes': state.source.sizeBytes,
          'fingerprint': state.source.fingerprint,
        },
        'model': state.model == null ? null : ModelMapper.toJson(state.model!),
      }),
    );
    if (!saved) throw const DomainError('storage');
  });
  @override
  Future<Result<void>> remove(String userId) => _serial(() async {
    final preferences = await SharedPreferences.getInstance();
    if (!await preferences.remove(_key(userId))) {
      throw const DomainError('storage');
    }
  });
}
