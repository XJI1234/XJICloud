import '../../../shared/domain/domain_error.dart';

abstract final class ModelPolicy {
  static const maxSizeBytes = 2 * 1024 * 1024 * 1024;
  static DomainError? validate(String name, int size) =>
      size < 1 ||
          size > maxSizeBytes ||
          name.length > 255 ||
          name.contains(RegExp(r'[/\\\x00-\x1f]')) ||
          !RegExp(r'^.+\.(ply|spz)$', caseSensitive: false).hasMatch(name)
      ? const DomainError('model_validation')
      : null;
}
