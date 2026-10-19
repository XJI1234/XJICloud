import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/presentation/widgets/app_button.dart';
import 'package:xjicloud_mobile/presentation/widgets/app_sheet.dart';
import 'package:xjicloud_mobile/shared/l10n/app_localizations.dart';

void main() {
  testWidgets(
    'button can become loading while held without a build lifecycle error',
    (tester) async {
      final loading = ValueNotifier(false);
      addTearDown(loading.dispose);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          home: Scaffold(
            body: ValueListenableBuilder(
              valueListenable: loading,
              builder: (_, value, _) => AppButton(
                label: 'Submit',
                loadingLabel: 'Submitting',
                isLoading: value,
                onPressed: () {},
              ),
            ),
          ),
        ),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Submit')),
      );
      await tester.pump(const Duration(milliseconds: 100));
      loading.value = true;
      await tester.pumpAndSettle();
      expect(find.text('Submitting'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await gesture.up();
    },
  );

  testWidgets('upload chooser returns the chosen action and supports cancel', (
    tester,
  ) async {
    UploadChoice? chosen;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('zh'),
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                chosen = await showUploadChooser(context: context);
              },
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('上传 PLY/SPZ 模型'));
    await tester.pumpAndSettle();
    expect(chosen, UploadChoice.model);
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('上传图片并训练'));
    await tester.pumpAndSettle();
    expect(chosen, UploadChoice.images);
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(chosen, isNull);
  });

  testWidgets('button press scales down and reduced motion keeps it still', (
    tester,
  ) async {
    Future<void> mount(bool reduced) => tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light,
        home: MediaQuery(
          data: MediaQueryData(disableAnimations: reduced),
          child: Scaffold(
            body: AppButton(label: 'Submit', onPressed: () {}),
          ),
        ),
      ),
    );
    await mount(false);
    final gesture = await tester.startGesture(
      tester.getCenter(find.text('Submit')),
    );
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.widget<AnimatedScale>(find.byType(AnimatedScale)).scale, .97);
    await gesture.up();
    await mount(true);
    final reducedGesture = await tester.startGesture(
      tester.getCenter(find.text('Submit')),
    );
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.widget<AnimatedScale>(find.byType(AnimatedScale)).scale, 1);
    await reducedGesture.up();
  });

  testWidgets(
    'loading button explains progress and prevents duplicate submission',
    (tester) async {
      var calls = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          home: Scaffold(
            body: AppButton(
              label: 'Submit',
              loadingLabel: 'Submitting',
              isLoading: true,
              onPressed: () => calls++,
            ),
          ),
        ),
      );
      await tester.tap(find.text('Submitting'));
      expect(calls, 0);
      expect(
        tester.getSize(find.byType(AppButton)).height,
        greaterThanOrEqualTo(48),
      );
    },
  );

  for (final width in [360.0, 1280.0]) {
    testWidgets(
      'form overlay at $width scrolls above keyboard with large font',
      (tester) async {
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = Size(width, 800);
        tester.view.viewInsets = FakeViewPadding(bottom: 300);
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.light,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: TextScaler.linear(2),
                disableAnimations: true,
              ),
              child: child!,
            ),
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () => showAppSheet<void>(
                    context: context,
                    builder: (_) => Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (var i = 0; i < 10; i++)
                          TextFormField(
                            decoration: InputDecoration(labelText: 'Field $i'),
                          ),
                        AppButton(
                          label: 'Submit',
                          onPressed: () => Navigator.pop(context),
                        ),
                      ],
                    ),
                  ),
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('Submit'));
        await tester.pumpAndSettle();
        expect(
          tester.getBottomLeft(find.text('Submit')).dy,
          lessThanOrEqualTo(500),
        );
        expect(
          find.byType(Dialog),
          width >= 840 ? findsOneWidget : findsNothing,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
}
