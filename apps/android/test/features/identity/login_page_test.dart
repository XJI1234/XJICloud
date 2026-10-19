import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/presentation/login_page.dart';
import 'package:xjicloud_mobile/features/identity/presentation/auth_controller.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'auth_use_cases_test.dart' show FakeAuth;

class CaptchaAuth extends FakeAuth {
  @override
  Future<Result<bool>> needCaptcha(String username) async =>
      const Success(true);
}

void main() {
  for (final registration in [true, false]) {
    testWidgets(
      'keyboard Next advances ${registration ? 'registration' : 'captcha login'} without submitting or validating untouched fields',
      (tester) async {
        final repo = registration ? FakeAuth() : CaptchaAuth();
        final controller = AuthController(
          repo,
          activate: (_) async => const Success(null),
        );
        addTearDown(controller.dispose);
        if (registration) {
          controller.setRegistration(true);
        } else {
          await controller.checkUsername('alice');
        }
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.light,
            locale: const Locale('en'),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: LoginPage(controller: controller, onToggleLocale: () {}),
          ),
        );
        await tester.pumpAndSettle();
        await tester.enterText(find.byKey(const Key('username')), 'alice');
        // Wait out the username lookup before checking the password IME action.
        await tester.pump(const Duration(milliseconds: 400));
        await tester.pumpAndSettle();
        await tester.enterText(find.byKey(const Key('password')), 'secret');
        await tester.testTextInput.receiveAction(TextInputAction.next);
        await tester.pumpAndSettle();
        expect(repo.calls, 0);
        expect(find.text('Passwords do not match.'), findsNothing);
        expect(find.text('This field is required.'), findsNothing);
        final nextField = find.descendant(
          of: find.byKey(
            Key(registration ? 'confirm-password' : 'captcha-code'),
          ),
          matching: find.byType(EditableText),
        );
        expect(
          tester.widget<EditableText>(nextField).focusNode.hasFocus,
          isTrue,
        );
      },
    );
  }
  testWidgets(
    'keyboard Done submits a login whose password is the last field',
    (tester) async {
      final repo = FakeAuth();
      int activated = 0;
      final controller = AuthController(
        repo,
        activate: (_) async {
          activated++;
          return const Success(null);
        },
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: LoginPage(controller: controller, onToggleLocale: () {}),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('username')), 'alice');
      await tester.enterText(find.byKey(const Key('password')), 'secret');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(repo.calls, 1);
      expect(activated, 1);
    },
  );
  for (final size in [
    const Size(360, 800),
    const Size(800, 360),
    const Size(800, 1280),
    const Size(1280, 800),
  ]) {
    testWidgets(
      'real login/register remains scrollable at 200% and keyboard $size',
      (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final controller = AuthController(
          FakeAuth(),
          activate: (_) async => const Success(null),
        );
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.light,
            locale: const Locale('en'),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: const TextScaler.linear(2),
                viewInsets: const EdgeInsets.only(bottom: 250),
              ),
              child: child!,
            ),
            home: LoginPage(controller: controller, onToggleLocale: () {}),
          ),
        );
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.byKey(const Key('auth-mode')));
        await tester.tap(find.byKey(const Key('auth-mode')));
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('confirm-password')), findsOneWidget);
        expect(find.byKey(const Key('captcha-code')), findsOneWidget);
        await tester.ensureVisible(find.byKey(const Key('auth-submit')));
        await tester.tap(find.byKey(const Key('auth-submit')));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(find.byType(TextFormField), findsNWidgets(5));
      },
    );
  }
}
