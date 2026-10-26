import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/identity/domain/locale_store.dart';
import 'package:xjicloud_mobile/features/identity/domain/account_profile.dart';
import 'package:xjicloud_mobile/features/identity/domain/user_session.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'session_policy_test.dart' show MemoryStore, session;
import 'auth_use_cases_test.dart' show FakeAuth;

class MemoryLocale implements LocaleStore {
  String? locale;
  @override
  Future<Result<String?>> read() async => Success(locale);
  @override
  Future<Result<void>> write(String value) async {
    locale = value;
    return const Success(null);
  }
}

class PendingProfileAuth extends FakeAuth {
  @override Future<Result<AccountProfile>> getProfile() async => const Success(AccountProfile(
    userId: 'alice', username: 'alice', phone: '13800138000', displayName: 'Alice',
    email: '', organization: '', profileRequired: true));
  @override Future<Result<AccountProfile>> updateProfile({required String displayName, required String email, required String organization}) async => Success(AccountProfile(
    userId: 'alice', username: 'alice', phone: '13800138000', displayName: displayName,
    email: email, organization: organization, profileRequired: false));
}

void main() {
  testWidgets('restored pending profile redirects deep links and saving unlocks account', (tester) async {
    final store = MemoryStore()..value = UserSession(userId: 'alice', username: 'alice', displayName: 'Alice',
      token: 'secret', expiresAt: DateTime.utc(2030), profileRequired: true);
    final sessions = SessionCoordinator(store);
    final container = AppContainer(sessions: sessions, auth: PendingProfileAuth(),
      locales: MemoryLocale()..locale = 'en', closeTransport: () {});
    await tester.pumpWidget(ProductApp(container: container, initialLocation: '/account/cache'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('profile-save')), findsOneWidget);
    expect(find.byKey(const Key('account-username')), findsNothing);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('profile-save')), findsOneWidget);
    await tester.enterText(find.byKey(const Key('profile-display-name')), 'Updated');
    await tester.enterText(find.byKey(const Key('profile-email')), 'alice@example.com');
    await tester.enterText(find.byKey(const Key('profile-organization')), 'Lab');
    await tester.ensureVisible(find.byKey(const Key('profile-save')));
    await tester.tap(find.byKey(const Key('profile-save')));
    await tester.pumpAndSettle();
    expect(sessions.session?.profileRequired, isFalse);
    expect(store.value?.displayName, 'Updated');
    expect(find.byKey(const Key('account-username')), findsOneWidget);
    expect(find.text('Updated'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'allowlisted redirect, logout and system Back never restore protected account',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      final container = AppContainer(
        sessions: sessions,
        auth: FakeAuth(),
        locales: MemoryLocale(),
        closeTransport: () {},
      );
      await tester.pumpWidget(
        ProductApp(
          container: container,
          initialLocation: '/login?from=https%3A%2F%2Fevil.test%2Faccount',
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('fusion-submit')), findsOneWidget);
      await sessions.activate(session());
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('account-username')), findsOneWidget);
      await tester.ensureVisible(find.byKey(const Key('account-logout')));
      await tester.tap(find.byKey(const Key('account-logout')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('fusion-submit')), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('account-username')), findsNothing);
      await sessions.activate(session('bob'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<SelectableText>(find.byKey(const Key('account-username')))
            .data,
        'bob',
      );
      await sessions.invalidate(expectedGeneration: sessions.generation);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('fusion-submit')), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
