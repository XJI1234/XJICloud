import 'package:shared_preferences/shared_preferences.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/locale_store.dart';

final class PreferencesLocaleStore implements LocaleStore {
  @override
  Future<Result<String?>> read() async {
    try {
      return Success(
        (await SharedPreferences.getInstance()).getString('locale'),
      );
    } on Object {
      return const Failure(DomainError('preferences'));
    }
  }

  @override
  Future<Result<void>> write(String locale) async {
    try {
      final ok = await (await SharedPreferences.getInstance()).setString(
        'locale',
        locale,
      );
      return ok
          ? const Success(null)
          : const Failure(DomainError('preferences'));
    } on Object {
      return const Failure(DomainError('preferences'));
    }
  }
}
