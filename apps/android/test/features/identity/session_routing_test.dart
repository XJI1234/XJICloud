import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/product_app.dart';
import 'package:xjicloud_mobile/app/app_container.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/identity/domain/locale_store.dart';
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

void main() {
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
      expect(find.byKey(const Key('username')), findsOneWidget);
      await sessions.activate(session());
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('account-username')), findsOneWidget);
      await tester.ensureVisible(find.byKey(const Key('account-logout')));
      await tester.tap(find.byKey(const Key('account-logout')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('username')), findsOneWidget);
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
      expect(find.byKey(const Key('username')), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
