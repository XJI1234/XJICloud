/// A stable error code, never a raw HTTP/SDK exception.
///
/// Adapters may supply already redacted [details] for diagnostics. Credentials,
/// presigned URLs and server exception bodies must never be included.
final class DomainError {
  const DomainError(this.code, {this.details});

  final String code;
  final String? details;
}
