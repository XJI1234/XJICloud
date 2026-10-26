import '../features/identity/domain/session_access.dart';
import '../features/identity/domain/user_session.dart';
import '../shared/domain/result.dart';

/// Business controllers start account work only after the profile gate clears.
final class CompletedProfileAccess implements SessionAccess {
  const CompletedProfileAccess(this.source);
  final SessionAccess source;
  @override UserSession? get session => source.session?.profileRequired == true ? null : source.session;
  @override int get generation => source.generation;
  @override Future<Result<void>> invalidate({int? expectedGeneration}) => source.invalidate(expectedGeneration: expectedGeneration);
}
