import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/presentation/login_page.dart';
import 'package:xjicloud_mobile/features/identity/presentation/auth_controller.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'auth_use_cases_test.dart' show FakeAuth;

void main() {
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
