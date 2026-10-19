import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../features/identity/infrastructure/http_auth_repository.dart';
import '../features/identity/infrastructure/secure_session_store.dart';
import '../features/identity/infrastructure/preferences_locale_store.dart';
import '../shared/infrastructure/api_client.dart';
import '../shared/infrastructure/sdk_licenses.dart';
import 'app_container.dart';
import 'session_coordinator.dart';

/// The sole infrastructure assembly point. No production default API is assumed.
AppContainer createContainer({
  String apiOrigin = const String.fromEnvironment('API_BASE_URL'),
}) {
  final sessions = SessionCoordinator(
    SecureSessionStore(const FlutterSecureStorage()),
  );
  final api = ApiClient(
    origin: apiOrigin,
    sessions: sessions,
    allowDebugHttp: kDebugMode,
  );
  registerSdkLicenses();
  return AppContainer(
    sessions: sessions,
    auth: HttpAuthRepository(api),
    locales: PreferencesLocaleStore(),
    closeTransport: api.close,
  );
}
