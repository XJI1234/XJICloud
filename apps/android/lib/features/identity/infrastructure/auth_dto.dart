final class AuthDto {
  AuthDto(Map<String, dynamic> json)
    : token = json['accessToken'] as String,
      tokenType = json['tokenType'] as String,
      userId = json['userId'] as String,
      username = json['username'] as String,
      displayName = json['displayName'] as String;
  final String token, tokenType, userId, username, displayName;
}
