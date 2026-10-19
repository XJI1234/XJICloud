import '../../../shared/domain/result.dart';

abstract interface class LocaleStore {
  Future<Result<String?>> read();
  Future<Result<void>> write(String locale);
}
