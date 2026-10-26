import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/domain/privacy_document.dart';
import 'package:xjicloud_mobile/features/identity/presentation/privacy_policy_page.dart';
import 'package:xjicloud_mobile/shared/domain/domain_error.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

void main() {
  for (final locale in ['zh', 'en']) {
    testWidgets(
      'bundled $locale policy stays readable at 200 percent on a phone',
      (tester) async {
        tester.view.physicalSize = const Size(360, 800);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final opened = <PrivacyDocument>[];
        await tester.pumpWidget(
          MaterialApp(
            locale: Locale(locale),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context)
                  .copyWith(textScaler: const TextScaler.linear(2)),
              child: child!,
            ),
            home: PrivacyPolicyPage(
              openDocument: (document) async {
                opened.add(document);
                return const Failure(DomainError('policy_unavailable'));
              },
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          opened,
          isEmpty,
          reason: 'The policy itself is bundled and starts no provider request',
        );
        final source = find.byKey(const Key('privacy-source-chinaMobile'));
        await tester.ensureVisible(source);
        await tester.tap(source);
        await tester.pumpAndSettle();
        expect(opened, [PrivacyDocument.chinaMobile]);
        expect(find.byType(SnackBar), findsOneWidget);
        final contact = find
            .byWidgetPredicate(
              (widget) =>
                  widget is SelectableText &&
                  (widget.data?.contains('baokx@seu.edu.cn') ?? false),
            )
            .last;
        await tester.ensureVisible(contact);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      },
    );
  }
}
