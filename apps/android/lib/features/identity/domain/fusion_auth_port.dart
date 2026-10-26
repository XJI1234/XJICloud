import '../../../shared/domain/result.dart';
import 'fusion_challenge.dart';
import 'privacy_document.dart';

abstract interface class FusionAuthPort {
  Future<Result<bool>> canAuthenticate();
  Future<Result<void>> openPrivacyDocument(PrivacyDocument document);
  Future<Result<String>> authenticate(FusionChallenge challenge);
  Future<void> complete(bool success);
  Future<void> cancel();
}
