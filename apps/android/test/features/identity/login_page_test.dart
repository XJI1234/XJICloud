import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/presentation/login_page.dart';
import 'package:xjicloud_mobile/features/identity/presentation/auth_controller.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import 'auth_use_cases_test.dart' show FakeAuth;
import 'auth_controller_test.dart' show FakeFusion;

import 'package:xjicloud_mobile/shared/domain/domain_error.dart';

Widget page(AuthController c, {TextScaler? scaler}) => MaterialApp(
  theme: AppTheme.light,
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      textScaler: scaler,
      viewInsets: const EdgeInsets.only(bottom: 250),
    ),
    child: child!,
  ),
  home: LoginPage(controller: c, onToggleLocale: () {}),
);

void main() {
  for (final mode in [AuthMode.password, AuthMode.registration]) {
    testWidgets('$mode keeps an offline privacy policy entry', (tester) async {
      final repo = FakeAuth();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      c.setRegistration(mode == AuthMode.registration);
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('privacy-policy')));
      await tester.tap(find.byKey(const Key('privacy-policy')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('privacy-policy-content')), findsOneWidget);
      expect(repo.fusionRequests, 0);
      expect(c.privacyAccepted, isFalse);
    });
  }
  testWidgets(
    'password backend error does not claim fusion authentication failed',
    (tester) async {
      final repo = FakeAuth()..outcome = const Failure(DomainError('server'));
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      c.setPasswordLogin();
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('username')), 'alice');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.enterText(find.byKey(const Key('password')), 'password');
      await tester.ensureVisible(find.byKey(const Key('auth-submit')));
      await tester.tap(find.byKey(const Key('auth-submit')));
      await tester.pumpAndSettle();
      final context = tester.element(find.byType(LoginPage));
      expect(
        find.text(AppLocalizations.of(context).errorRequest),
        findsOneWidget,
      );
      expect(
        find.text(AppLocalizations.of(context).fusionBackendFailed),
        findsNothing,
      );
    },
  );
  testWidgets(
    'policy link opens without granting consent or starting authentication',
    (tester) async {
      final repo = FakeAuth();
      final fusion = FakeFusion();
      final c = AuthController(
        repo,
        fusion: fusion,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('privacy-policy')));
      await tester.tap(find.byKey(const Key('privacy-policy')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('privacy-policy-content')), findsOneWidget);
      expect(find.textContaining('baokx@seu.edu.cn'), findsWidgets);
      expect(fusion.policyOpens, 0);
      expect(c.privacyAccepted, isFalse);
      expect(repo.fusionRequests, 0);
      await tester.ensureVisible(
        find.byKey(const Key('privacy-source-numberAuthentication')),
      );
      await tester.tap(
        find.byKey(const Key('privacy-source-numberAuthentication')),
      );
      await tester.pumpAndSettle();
      expect(fusion.policyOpens, 1);
      expect(c.privacyAccepted, isFalse);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(c.privacyAccepted, isFalse);
    },
  );
  testWidgets('registered phone response gives actionable login guidance', (
    tester,
  ) async {
    final repo = FakeAuth()
      ..smsOutcome = const Failure(DomainError('phone_registered'));
    final c = AuthController(repo, activate: (_) async => const Success(null));
    addTearDown(c.dispose);
    c.setRegistration(true);
    await tester.pumpWidget(page(c));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('phone')), '13800138000');
    await tester.enterText(find.byKey(const Key('captcha-code')), '1234');
    await tester.ensureVisible(find.byKey(const Key('sms-send')));
    await tester.tap(find.byKey(const Key('sms-send')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'This phone number is already registered. Sign in with your password.',
      ),
      findsOneWidget,
    );
    expect(c.smsChallenge, isNull);
  });
  testWidgets(
    'default one-tap entry never initializes before unchecked privacy consent',
    (tester) async {
      final repo = FakeAuth();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('fusion-submit')), findsOneWidget);
      expect(
        tester.widget<Checkbox>(find.byKey(const Key('privacy-consent'))).value,
        isFalse,
      );
      await tester.ensureVisible(find.byKey(const Key('fusion-submit')));
      await tester.tap(find.byKey(const Key('fusion-submit')));
      await tester.pumpAndSettle();
      expect(repo.fusionRequests, 0);
      expect(c.error?.code, 'privacy_required');
      expect(find.textContaining('Alibaba'), findsNothing);
      expect(find.byKey(const Key('privacy-policy')), findsOneWidget);
    },
  );
  testWidgets(
    'typing and IME rebuilds retain the captcha image and challenge',
    (tester) async {
      final repo = FakeAuth();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      c.setRegistration(true);
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      Image captcha() => tester
          .widgetList<Image>(find.byType(Image))
          .firstWhere((image) => image.image is MemoryImage);
      final provider = captcha().image;
      final requests = repo.captchaRequests;
      for (final phone in ['1', '13', '13800138000']) {
        await tester.enterText(find.byKey(const Key('phone')), phone);
        await tester.pumpAndSettle();
        expect(captcha().image, same(provider));
      }
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      addTearDown(tester.view.resetViewInsets);
      await tester.pumpAndSettle();
      expect(captcha().image, same(provider));
      expect(repo.captchaRequests, requests);
    },
  );
  testWidgets(
    'password Done submits while Next advances without untouched validation',
    (tester) async {
      final repo = FakeAuth();
      var activations = 0;
      final c = AuthController(
        repo,
        activate: (_) async {
          activations++;
          return const Success(null);
        },
      );
      addTearDown(c.dispose);
      c.setPasswordLogin();
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('username')), 'alice');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.enterText(find.byKey(const Key('password')), 'secret');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(repo.calls, 1);
      expect(activations, 1);
    },
  );
  testWidgets(
    'phone registration consumes image then allows SMS password registration and clears changed phone',
    (tester) async {
      final repo = FakeAuth();
      final c = AuthController(
        repo,
        activate: (_) async => const Success(null),
      );
      addTearDown(c.dispose);
      c.setRegistration(true);
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('phone')), '13800138000');
      await tester.enterText(find.byKey(const Key('captcha-code')), '1234');
      await tester.ensureVisible(find.byKey(const Key('sms-send')));
      await tester.tap(find.byKey(const Key('sms-send')));
      await tester.pumpAndSettle();
      expect(repo.smsRequests, 1);
      expect(find.byKey(const Key('captcha-code')), findsNothing);
      await tester.ensureVisible(find.byKey(const Key('sms-code')));
      await tester.enterText(find.byKey(const Key('sms-code')), '012345');
      await tester.ensureVisible(find.byKey(const Key('phone')));
      await tester.enterText(find.byKey(const Key('phone')), '13900139000');
      await tester.pumpAndSettle();
      expect(c.smsChallenge, isNull);
      expect(find.byKey(const Key('captcha-code')), findsOneWidget);
    },
  );
  testWidgets(
    'phone image SMS password confirmation completes registration with its session',
    (tester) async {
      final repo = FakeAuth();
      var activated = 0;
      final c = AuthController(
        repo,
        activate: (_) async {
          activated++;
          return const Success(null);
        },
      );
      addTearDown(c.dispose);
      c.setRegistration(true);
      await tester.pumpWidget(page(c));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('phone')), '13800138000');
      await tester.enterText(find.byKey(const Key('captcha-code')), '1234');
      await tester.ensureVisible(find.byKey(const Key('sms-send')));
      await tester.tap(find.byKey(const Key('sms-send')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('sms-code')), '012345');
      await tester.enterText(find.byKey(const Key('password')), 'password');
      await tester.testTextInput.receiveAction(TextInputAction.next);
      await tester.pump();
      final confirmation = find.descendant(
        of: find.byKey(const Key('confirm-password')),
        matching: find.byType(EditableText),
      );
      expect(
        tester.widget<EditableText>(confirmation).focusNode.hasFocus,
        isTrue,
      );
      expect(repo.calls, 0);
      await tester.enterText(
        find.byKey(const Key('confirm-password')),
        'password',
      );
      await tester.ensureVisible(find.byKey(const Key('auth-submit')));
      await tester.tap(find.byKey(const Key('auth-submit')));
      await tester.pumpAndSettle();
      expect(activated, 1);
      expect(repo.calls, 1);
      expect(repo.lastSmsCode, '012345');
    },
  );
  for (final size in [
    const Size(360, 800),
    const Size(800, 360),
    const Size(800, 1280),
    const Size(1280, 800),
  ]) {
    testWidgets(
      'phone registration scrolls at 200 percent with keyboard $size',
      (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final c = AuthController(
          FakeAuth(),
          activate: (_) async => const Success(null),
        );
        addTearDown(c.dispose);
        c.setRegistration(true);
        await tester.pumpWidget(page(c, scaler: const TextScaler.linear(2)));
        await tester.pumpAndSettle();
        final input = tester.getRect(find.byKey(const Key('captcha-code')));
        final image = tester.getRect(
          find.byWidgetPredicate(
            (widget) => widget is Image && widget.image is MemoryImage,
          ),
        );
        expect(image.left, greaterThan(input.right));
        expect(image.top, lessThan(input.bottom));
        expect(image.bottom, greaterThan(input.top));
        await tester.ensureVisible(find.byKey(const Key('sms-send')));
        await tester.tap(find.byKey(const Key('sms-send')));
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('captcha-code')), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
