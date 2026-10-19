import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/identity/domain/session_store.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

UserSession session([String name = 'alice']) => UserSession(
  userId: name,
  username: name,
  displayName: name,
  token: 'secret',
  expiresAt: DateTime.utc(2030),
);

class MemoryStore implements SessionStore {
  UserSession? value;
  int clears = 0;
  Completer<void>? writeGate;
  bool failWrite = false;
  bool failClear = false;
  @override
  Future<Result<UserSession?>> read() async => Success(value);
  @override
  Future<Result<void>> write(UserSession s) async {
    await writeGate?.future;
    value = s;
    return failWrite
        ? const Failure(DomainError('storage'))
        : const Success(null);
  }

  @override
  Future<Result<void>> clear() async {
    clears++;
    if (failClear) return const Failure(DomainError('storage'));
    value = null;
    return const Success(null);
  }
}

void main() {
  test(
    'a previous logout cannot suppress logout during the next slow login',
    () async {
      final store = MemoryStore();
      final c = SessionCoordinator(store, now: () => DateTime.utc(2029));
      addTearDown(c.dispose);
      await c.activate(session());
      await c.invalidate();
      store.writeGate = Completer();
      final activation = c.activate(session('bob'));
      await Future<void>.delayed(Duration.zero);
      final logout = c.invalidate();
      store.writeGate!.complete();
      await Future.wait([activation, logout]);
      expect(c.session, isNull);
      expect(store.value, isNull);
    },
  );
  test(
    'a clear failure can be retried and cannot claim storage was cleared',
    () async {
      final store = MemoryStore()
        ..value = session()
        ..failClear = true;
      final c = SessionCoordinator(store);
      addTearDown(c.dispose);
      expect(await c.invalidate(), isA<Failure<void>>());
      expect(store.value, isNotNull);
      store.failClear = false;
      expect(await c.invalidate(), isA<Success<void>>());
      expect(store.value, isNull);
    },
  );
  test('a token expiring during secure write never becomes active', () async {
    var now = DateTime.utc(2029);
    final store = MemoryStore()..writeGate = Completer();
    final c = SessionCoordinator(store, now: () => now);
    addTearDown(c.dispose);
    final activation = c.activate(session());
    await Future<void>.delayed(Duration.zero);
    now = DateTime.utc(2030);
    store.writeGate!.complete();
    expect(await activation, isA<Failure<void>>());
    expect(c.session, isNull);
    expect(store.value, isNull);
  });
  test(
    'concurrent 401 invalidates once and old generation cannot clear relogin',
    () async {
      final store = MemoryStore();
      final c = SessionCoordinator(store, now: () => DateTime.utc(2029));
      addTearDown(c.dispose);
      await c.activate(session());
      final old = c.generation;
      int cleaned = 0;
      c.registerCleanup(() async {
        cleaned++;
      });
      await Future.wait([
        c.invalidate(expectedGeneration: old),
        c.invalidate(expectedGeneration: old),
      ]);
      expect(cleaned, 1);
      expect(store.clears, 1);
      expect(c.session, isNull);
      await c.activate(session('bob'));
      await c.invalidate(expectedGeneration: old);
      expect(c.session?.username, 'bob');
    },
  );
  test('logout racing delayed persistence cannot resurrect token', () async {
    final store = MemoryStore()..writeGate = Completer();
    final c = SessionCoordinator(store, now: () => DateTime.utc(2029));
    addTearDown(c.dispose);
    final activation = c.activate(session());
    final logout = c.invalidate();
    store.writeGate!.complete();
    await Future.wait([activation, logout]);
    expect(c.session, isNull);
    expect(store.value, isNull);
  });
  test(
    'write failure does not activate or leave recoverable session',
    () async {
      final store = MemoryStore()..failWrite = true;
      final c = SessionCoordinator(store, now: () => DateTime.utc(2029));
      addTearDown(c.dispose);
      expect(await c.activate(session()), isA<Failure<void>>());
      expect(c.session, isNull);
      expect(store.value, isNull);
    },
  );
  test(
    'foreground expiry clears session using UTC exp and reports clear failure',
    () async {
      var now = DateTime.utc(2029);
      final store = MemoryStore();
      final c = SessionCoordinator(store, now: () => now);
      addTearDown(c.dispose);
      await c.activate(session());
      now = DateTime.utc(2030);
      store.failClear = true;
      await c.checkExpiry();
      expect(c.session, isNull);
      expect(c.error?.code, 'storage');
    },
  );
}
