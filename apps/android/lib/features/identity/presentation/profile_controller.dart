import 'package:flutter/foundation.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../application/save_account_profile.dart';
import '../domain/account_profile.dart';
import '../domain/auth_repository.dart';

final class ProfileController extends ChangeNotifier {
  ProfileController(this.repository, {required this.applyProfile});
  final AuthRepository repository;
  final Future<Result<void>> Function(AccountProfile) applyProfile;
  AccountProfile? profile;
  DomainError? error;
  bool loading = false, saving = false, saved = false, _disposed = false;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    if (loading || saving || _disposed) return;
    loading = true;
    error = null;
    _notify();
    final result = await repository.getProfile();
    if (_disposed) return;
    if (result case Success<AccountProfile>(:final value)) {
      final applied = await applyProfile(value);
      if (_disposed) return;
      if (applied case Failure<void>(:final error)) {
        this.error = error;
      } else {
        profile = value;
      }
    } else if (result case Failure<AccountProfile>(:final error)) {
      this.error = error;
    }
    loading = false;
    _notify();
  }

  Future<void> save({
    required String displayName,
    required String email,
    required String organization,
  }) async {
    if (saving || loading || _disposed) return;
    saving = true;
    saved = false;
    error = null;
    _notify();
    final result = await SaveAccountProfile(repository)(
      displayName: displayName,
      email: email,
      organization: organization,
    );
    if (_disposed) return;
    if (result case Success<AccountProfile>(:final value)) {
      final applied = await applyProfile(value);
      if (_disposed) return;
      if (applied case Failure<void>(:final error)) {
        this.error = error;
      } else {
        profile = value;
        saved = true;
      }
    } else if (result case Failure<AccountProfile>(:final error)) {
      this.error = error;
    }
    saving = false;
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
