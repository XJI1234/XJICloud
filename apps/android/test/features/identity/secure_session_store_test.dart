import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/infrastructure/secure_session_store.dart';
import 'package:xjicloud_mobile/features/identity/infrastructure/auth_mapper.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'auth_mapper_test.dart' show response;

class ControlledStorage extends FlutterSecureStorage {
  String? value;
  Completer<void>? gate;
  bool failRead = false, failDelete = false;
  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (failRead) throw StateError('private platform text');
    return value;
  }

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    await gate?.future;
    this.value = value;
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (failDelete) throw StateError('private delete error');
    value = null;
  }
}

void main() {
  test('secure write, clear and read preserve enqueue order', () async {
    final storage = ControlledStorage()..gate = Completer();
    final store = SecureSessionStore(storage);
    final write = store.write(AuthMapper.session(response()));
    final clear = store.clear();
    final read = store.read();
    storage.gate!.complete();
    await write;
    await clear;
    expect((await read as Success<UserSession?>).value, isNull);
    expect(storage.value, isNull);
  });
  test('restores authoritative JWT exp and never persists password', () async {
    final storage = ControlledStorage();
    final store = SecureSessionStore(storage);
    await store.write(AuthMapper.session(response()));
    expect(jsonDecode(storage.value!).containsKey('password'), isFalse);
    final restored = (await store.read() as Success<UserSession?>).value!;
    expect(restored.expiresAt, DateTime.utc(2033, 5, 18, 3, 33, 20));
  });
  test('corrupt record clears; read and delete errors remain failures with redacted codes', () async {
    final storage = ControlledStorage()..value = 'not-json';
    final store = SecureSessionStore(storage);
    expect(await store.read(), isA<Failure<UserSession?>>());
    expect(storage.value, isNull);
    storage.failRead = true;
    expect((await store.read() as Failure).error.code, 'storage');
    storage.failDelete = true;
    expect((await store.clear() as Failure).error.code, 'storage');
  });
}
