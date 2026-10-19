import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/presentation/pages/my_page.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

import 'session_policy_test.dart' show session;

void main() {
  testWidgets(
    'offline help provides reachable About and open source licenses',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: MyPage(
            session: session(),
            onToggleLocale: () {},
            onLogout: () {},
          ),
        ),
      );
      await tester.ensureVisible(find.byKey(const Key('account-help')));
      await tester.tap(find.byKey(const Key('account-help')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Native3DGS SDK 0.2.2'), findsOneWidget);
      await tester.ensureVisible(find.byKey(const Key('view-licenses')));
      await tester.tap(find.byKey(const Key('view-licenses')));
      await tester.pumpAndSettle();
      expect(find.byType(LicensePage), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
