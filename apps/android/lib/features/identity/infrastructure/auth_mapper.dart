import 'dart:convert';

import '../domain/user_session.dart';
import '../domain/account_profile.dart';
import '../domain/captcha_challenge.dart';
import 'auth_dto.dart';

abstract final class AuthMapper {
  static UserSession session(Map<String, dynamic> json) {
    final dto = AuthDto(json);
    final parts = dto.token.split('.');
    if (parts.length != 3 || dto.tokenType != 'Bearer') {
      throw const FormatException('Invalid credential metadata');
    }
    final claims = jsonDecode(
      utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
    ) as Map<String, dynamic>;
    if (claims['type'] != 'user' ||
        claims['sub'] != dto.userId ||
        claims['username'] != dto.username ||
        claims['exp'] is! int ||
        dto.userId.isEmpty ||
        dto.username.isEmpty) {
      throw const FormatException('Invalid user credential');
    }
    return UserSession(
      userId: dto.userId,
      username: dto.username,
      displayName: dto.displayName,
      token: dto.token,
      profileRequired: dto.profileRequired,
      expiresAt: DateTime.fromMillisecondsSinceEpoch(
        (claims['exp'] as int) * 1000,
        isUtc: true,
      ),
    );
  }

  static AccountProfile profile(Map<String, dynamic> json) => AccountProfile(
    userId: json['userId'] as String,
    username: json['username'] as String,
    phone: json['phone'] as String?,
    displayName: json['displayName'] as String,
    email: json['email'] as String? ?? '',
    organization: json['organization'] as String? ?? '',
    profileRequired: json['profileRequired'] as bool,
  );

  static CaptchaChallenge captcha(Map<String, dynamic> json) {
    final key = json['captchaKey'] as String;
    final image = json['captchaImage'] as String;
    const prefix = 'data:image/png;base64,';
    if (key.isEmpty || !image.startsWith(prefix)) {
      throw const FormatException('Invalid captcha');
    }
    final bytes = base64Decode(image.substring(prefix.length));
    if (bytes.isEmpty) throw const FormatException('Empty captcha');
    return CaptchaChallenge(key: key, imageBytes: bytes);
  }
}
