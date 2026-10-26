import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/infrastructure/auth_mapper.dart';

String jwt({String type = 'user', String sub = 'abc', int exp = 2000000000}) =>
    '${base64Url.encode(utf8.encode('{"alg":"HS256"}')).replaceAll('=', '')}.${base64Url.encode(utf8.encode(jsonEncode({'type': type, 'sub': sub, 'username': 'alice', 'exp': exp}))).replaceAll('=', '')}.signature';
Map<String, dynamic> response() => {
  'accessToken': jwt(),
  'tokenType': 'Bearer',
  'expiresInMs': 604800000,
  'userId': 'abc',
  'username': 'alice',
  'displayName': 'Alice',
};
void main() {
  test('JWT exp controls expiry instead of expiresInMs', () {
    final session = AuthMapper.session(response());
    expect(session.expiresAt, DateTime.utc(2033, 5, 18, 3, 33, 20));
    expect(session.expiresAt.isUtc, isTrue);
  });
  test('rejects admin token, different subject and inconsistent metadata', () {
    for (final token in [jwt(type: 'admin'), jwt(sub: 'other')]) {
      expect(
        () => AuthMapper.session({...response(), 'accessToken': token}),
        throwsFormatException,
      );
    }
    expect(
      () => AuthMapper.session({...response(), 'tokenType': 'Basic'}),
      throwsFormatException,
    );
  });
  test('captcha consumes actual data image without imaginary expiry', () {
    final captcha = AuthMapper.captcha({
      'captchaKey': 'challenge',
      'captchaImage': 'data:image/png;base64,aGVsbG8=',
    });
    expect(captcha.key, 'challenge');
    expect(captcha.imageBytes, utf8.encode('hello'));
  });
  test('profile gate and account data map to identity domain', () {
    final pending = AuthMapper.session({...response(), 'profileRequired': true});
    expect(pending.profileRequired, isTrue);
    final profile = AuthMapper.profile({
      'userId': 'abc', 'username': 'alice', 'phone': null,
      'displayName': 'New name', 'email': 'a@example.com',
      'organization': 'Lab', 'profileRequired': false,
    });
    final updated = pending.withProfile(profile);
    expect(updated.profileRequired, isFalse);
    expect(updated.displayName, 'New name');
    expect(updated.token, pending.token);
    expect(updated.expiresAt, pending.expiresAt);
    expect(() => pending.withProfile(AuthMapper.profile({
      'userId': 'different', 'username': 'alice', 'phone': null,
      'displayName': 'Wrong account', 'email': 'a@example.com',
      'organization': 'Lab', 'profileRequired': false,
    })), throwsArgumentError);
  });
}
