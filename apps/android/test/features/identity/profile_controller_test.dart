import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/domain/account_profile.dart';
import 'package:xjicloud_mobile/features/identity/domain/auth_repository.dart';
import 'package:xjicloud_mobile/features/identity/presentation/profile_controller.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

const profile = AccountProfile(userId: 'alice', username: 'alice', phone: null,
  displayName: 'Alice', email: 'alice@example.com', organization: 'Lab', profileRequired: false);

class Profiles implements AuthRepository {
  int saves = 0;
  Completer<Result<AccountProfile>>? gate;
  @override Future<Result<AccountProfile>> getProfile() async => const Success(profile);
  @override Future<Result<AccountProfile>> updateProfile({required String displayName, required String email, required String organization}) async {
    saves++;
    return gate == null ? const Success(profile) : gate!.future;
  }
  @override dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  test('invalid profile stays local and valid profile updates the session', () async {
    final repo = Profiles();
    int applied = 0;
    final c = ProfileController(repo, applyProfile: (_) async { applied++; return const Success(null); });
    addTearDown(c.dispose);
    await c.load();
    expect(c.profile?.userId, 'alice');
    await c.save(displayName: '', email: 'bad', organization: '');
    expect(repo.saves, 0);
    expect(c.error?.code, 'profile_validation');
    await c.save(displayName: 'Alice', email: 'alice@example.com', organization: 'Lab');
    expect(repo.saves, 1);
    expect(c.saved, isTrue);
    expect(applied, 2); // load also refreshes the authoritative profile gate.
  });
  test('duplicate save and completion after disposal cannot update identity', () async {
    final repo = Profiles()..gate = Completer();
    int applied = 0;
    final c = ProfileController(repo, applyProfile: (_) async { applied++; return const Success(null); });
    final save = c.save(displayName: 'Alice', email: 'alice@example.com', organization: 'Lab');
    await c.save(displayName: 'Alice', email: 'alice@example.com', organization: 'Lab');
    expect(repo.saves, 1);
    c.dispose();
    repo.gate!.complete(const Success(profile));
    await save;
    expect(applied, 0);
  });
}
