import 'dart:async';

import 'package:flutter/foundation.dart';

import '../features/identity/domain/session_access.dart';
import '../features/identity/domain/session_store.dart';
import '../features/identity/domain/user_session.dart';
import '../features/identity/domain/account_profile.dart';
import '../features/identity/application/restore_session.dart';
import '../shared/domain/domain_error.dart';
import '../shared/domain/result.dart';

/// Invalidate memory synchronously; serialize persistence and cleanup afterwards.
final class SessionCoordinator extends ChangeNotifier implements SessionAccess {
  SessionCoordinator(this.store, {DateTime Function()? now})
    : now = now ?? DateTime.now;
  final SessionStore store;
  final DateTime Function() now;
  @override
  UserSession? get session => _session;
  UserSession? _session;
  @override
  int get generation => _generation;
  int _generation = 0;
  DomainError? error;
  Timer? _timer;
  Future<void> _queue = Future.value();
  Future<Result<void>>? _invalidating;
  final List<Future<void> Function()> _cleanup = [];
  bool _disposed = false;
  void registerCleanup(Future<void> Function() hook) => _cleanup.add(hook);
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<T> _serial<T>(Future<T> Function() operation) {
    final result = _queue.then((_) => operation());
    _queue = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  Future<void> restore() async {
    final ticket = _generation;
    final result = await _serial(() => RestoreSession(store)(now()));
    if (ticket != _generation || _disposed) return;
    switch (result) {
      case Success<UserSession?>(:final value):
        _session = value;
        error = null;
        _schedule();
      case Failure<UserSession?>(:final error):
        this.error = error;
    }
    _notify();
  }

  Future<Result<void>> activate(UserSession session) async {
    final ticket = ++_generation;
    _invalidating = null;
    _timer?.cancel();
    _session = null;
    error = null;
    _notify();
    return _serial(() async {
      if (ticket != _generation || _disposed) {
        return const Failure(DomainError('cancelled'));
      }
      if (session.isExpired(now())) {
        return const Failure(DomainError('expired'));
      }
      final result = await store.write(session);
      if (result is Failure<void> ||
          ticket != _generation ||
          _disposed ||
          session.isExpired(now())) {
        final cleared = await store.clear();
        if (cleared case Failure<void>(:final error)) {
          this.error = error;
          _notify();
          return Failure(error);
        }
        if (result case Failure<void>(:final error)) {
          this.error = error;
          _notify();
          return Failure(error);
        }
        return Failure(
          DomainError(session.isExpired(now()) ? 'expired' : 'cancelled'),
        );
      }
      _session = session;
      _invalidating = null;
      _schedule();
      _notify();
      return const Success(null);
    });
  }

  @override
  Future<Result<void>> invalidate({int? expectedGeneration}) {
    if (expectedGeneration != null && expectedGeneration != _generation) {
      return Future.value(const Success(null));
    }
    if (_invalidating != null) return _invalidating!;
    ++_generation;
    _session = null;
    _timer?.cancel();
    error = null;
    _notify();
    final future = _serial(() async {
      final result = await store.clear();
      DomainError? failure;
      if (result case Failure<void>(:final error)) failure = error;
      for (final hook in _cleanup) {
        try {
          await hook();
        } catch (_) {
          failure ??= const DomainError('cleanup');
        }
      }
      error = failure;
      _notify();
      return failure == null
          ? const Success<void>(null)
          : Failure<void>(failure);
    });
    _invalidating = future;
    future.then((result) {
      if (result is Failure<void> && identical(_invalidating, future)) {
        _invalidating = null;
      }
    });
    return future;
  }

  Future<Result<void>> updateProfile(AccountProfile profile, {required int expectedGeneration}) => _serial(() async {
    final current = _session;
    if (_disposed || current == null || expectedGeneration != _generation ||
        current.userId != profile.userId || current.username != profile.username) {
      return const Failure(DomainError('cancelled'));
    }
    if (current.isExpired(now())) return const Failure(DomainError('expired'));
    final updated = current.withProfile(profile);
    final saved = await store.write(updated);
    if (_disposed || expectedGeneration != _generation) {
      return const Failure(DomainError('cancelled'));
    }
    if (saved is Failure<void>) return saved;
    if (updated.isExpired(now())) return const Failure(DomainError('expired'));
    _session = updated;
    error = null;
    _notify();
    return const Success(null);
  });

  Future<void> checkExpiry() async {
    if (_session?.isExpired(now()) ?? false) {
      await invalidate(expectedGeneration: _generation);
    }
  }

  void _schedule() {
    _timer?.cancel();
    final session = _session;
    if (session != null) {
      final ticket = _generation;
      _timer = Timer(
        session.expiresAt.difference(now()),
        () => invalidate(expectedGeneration: ticket),
      );
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    super.dispose();
  }
}
