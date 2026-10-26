import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/session_store.dart';
import '../domain/user_session.dart';
import 'auth_mapper.dart';

/// A single encrypted record; reads/writes/deletes cannot overtake one another.
final class SecureSessionStore implements SessionStore {
  SecureSessionStore(this.storage);
  final FlutterSecureStorage storage;
  static const key = 'xjicloud.user_session';
  Future<void> _queue = Future.value();
  Future<Result<T>> _serial<T>(Future<Result<T>> Function() op) {
    final result = _queue.then((_) => op());
    _queue = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  @override
  Future<Result<UserSession?>> read() => _serial(() async {
    try {
      final raw = await storage.read(key: key);
      if (raw == null) return const Success(null);
      try {
        return Success(
          AuthMapper.session(jsonDecode(raw) as Map<String, dynamic>),
        );
      } on Object {
        await storage.delete(key: key);
        return const Failure(DomainError('session_invalid'));
      }
    } on Object {
      return const Failure(DomainError('storage'));
    }
  });
  @override
  Future<Result<void>> write(UserSession session) => _serial(() async {
    try {
      await storage.write(
        key: key,
        value: jsonEncode({
          'accessToken': session.token,
          'tokenType': 'Bearer',
          'userId': session.userId,
          'username': session.username,
          'displayName': session.displayName,
          'profileRequired': session.profileRequired,
        }),
      );
      return const Success(null);
    } on Object {
      return const Failure(DomainError('storage'));
    }
  });
  @override
  Future<Result<void>> clear() => _serial(() async {
    try {
      await storage.delete(key: key);
      return const Success(null);
    } on Object {
      return const Failure(DomainError('storage'));
    }
  });
}
