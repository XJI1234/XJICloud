final class AuthDto {
  AuthDto(Map<String, dynamic> json)
    : token = json['accessToken'] as String,
      tokenType = json['tokenType'] as String,
      userId = json['userId'] as String,
      username = json['username'] as String,
      displayName = json['displayName'] as String,
      // Old encrypted records lack this field; preserve legacy sessions until
      // the server returns an authoritative profile gate.
      profileRequired = json['profileRequired'] as bool? ?? false;
  final String token, tokenType, userId, username, displayName;
  final bool profileRequired;
}
