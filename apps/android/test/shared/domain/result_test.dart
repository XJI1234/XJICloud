import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

void main() {
  test('Result keeps failure distinct from an empty list', () {
    const Result<List<String>> result = Failure(DomainError('NETWORK'));
    expect(result, isA<Failure<List<String>>>());
    expect(switch (result) {
      Success(value: final value) => value,
      Failure(error: final error) => error.code,
    }, 'NETWORK');
    const Result<List<String>> empty = Success([]);
    expect(empty, isA<Success<List<String>>>());
  });
}
