import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xjicloud_mobile/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const username = String.fromEnvironment('username');
  const password = String.fromEnvironment('password');
  testWidgets(
    'real login, account, language, offline help, logout and relogin',
    (tester) async {
      expect(
        username,
        isNotEmpty,
        reason: 'Pass external credentials with --dart-define-from-file.',
      );
      expect(password, isNotEmpty);
      app.main();
      await tester.pumpAndSettle();
      // Plugin IO is independent of frame settling. Wait for a real product state.
      for (
        var i = 0;
        i < 120 &&
            find.byKey(const Key('username')).evaluate().isEmpty &&
            find.byKey(const Key('account-logout')).evaluate().isEmpty;
        i++
      ) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(
        find.byKey(const Key('username')).evaluate().isNotEmpty ||
            find.byKey(const Key('account-logout')).evaluate().isNotEmpty,
        isTrue,
        reason: 'Session restoration must reach login or account.',
      );
      Future<void> visible(Key key) async {
        await tester.ensureVisible(find.byKey(key));
        await tester.pumpAndSettle();
      }

      Future<void> login() async {
        await tester.enterText(find.byKey(const Key('username')), username);
        await tester.enterText(find.byKey(const Key('password')), password);
        await tester.pump(const Duration(milliseconds: 600));
        await tester.pumpAndSettle();
        await visible(const Key('auth-submit'));
        await tester.tap(find.byKey(const Key('auth-submit')));
        await tester.pumpAndSettle();
        // Real device networking completes independently of Flutter frame settling.
        for (
          var i = 0;
          i < 80 &&
              find.byKey(const Key('account-username')).evaluate().isEmpty;
          i++
        ) {
          await tester.pump(const Duration(milliseconds: 250));
        }
        expect(find.byKey(const Key('account-username')), findsOneWidget);
        expect(find.text(username), findsOneWidget);
      }

      // A persisted prior run may have restored the account.
      if (find.byKey(const Key('account-logout')).evaluate().isNotEmpty) {
        await visible(const Key('account-logout'));
        await tester.tap(find.byKey(const Key('account-logout')));
        await tester.pumpAndSettle();
      }
      await login();
      await visible(const Key('account-language'));
      await tester.tap(find.byKey(const Key('account-language')));
      await tester.pumpAndSettle();
      await visible(const Key('account-help'));
      await tester.tap(find.byKey(const Key('account-help')));
      await tester.pumpAndSettle();
      await visible(const Key('help-close'));
      await tester.tap(find.byKey(const Key('help-close')));
      await tester.pumpAndSettle();
      await visible(const Key('account-logout'));
      await tester.tap(find.byKey(const Key('account-logout')));
      await tester.pumpAndSettle();
      // Root Android Back exits the Activity. Its guard/relaunch path is checked
      // by the device driver; do not kill the integration test host mid-flow.
      expect(find.byKey(const Key('account-username')), findsNothing);
      expect(find.byKey(const Key('username')), findsOneWidget);
      await login();
      expect(tester.takeException(), isNull);
    },
  );
}
