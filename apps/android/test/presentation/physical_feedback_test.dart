import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/presentation/theme/app_theme.dart';
import 'package:xjicloud_mobile/presentation/widgets/app_button.dart';

void main() {
  Widget host({bool reduced = false, bool enabled = true}) => MaterialApp(
    theme: AppTheme.light,
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: reduced),
      child: Scaffold(
        body: Center(
          child: AppButton(
            label: 'Open model',
            onPressed: enabled ? () {} : null,
          ),
        ),
      ),
    ),
  );

  testWidgets('a released press carries its velocity without a visual snap', (
    tester,
  ) async {
    await tester.pumpWidget(host());
    final button = find.byType(TextButton);
    final original = tester.getRect(button).width;
    final gesture = await tester.startGesture(tester.getCenter(button));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 25));
    final pressed = tester.getRect(button).width;
    expect(pressed, lessThan(original));
    await gesture.up();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 5));
    // A spring retains inward velocity briefly when the target is reversed.
    // Restarting a fixed-duration tween reverses velocity abruptly instead.
    expect(tester.getRect(button).width, lessThan(pressed));
    await tester.pumpAndSettle();
    expect(tester.getRect(button).width, closeTo(original, .1));
  });

  testWidgets('reduced motion and disabling a held button reset its geometry', (
    tester,
  ) async {
    await tester.pumpWidget(host(reduced: true));
    final button = find.byType(TextButton);
    final original = tester.getRect(button);
    final gesture = await tester.startGesture(tester.getCenter(button));
    await tester.pump(const Duration(milliseconds: 80));
    expect(tester.getRect(button), original);
    await gesture.cancel();
    await tester.pumpWidget(host());
    final held = await tester.startGesture(tester.getCenter(button));
    await tester.pumpAndSettle();
    await tester.pumpWidget(host(enabled: false));
    await tester.pumpAndSettle();
    expect(tester.getRect(button), original);
    await held.up();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'keyboard activation stays stationary and cancelled drag releases',
    (tester) async {
      await tester.pumpWidget(host());
      final button = find.byType(TextButton);
      final original = tester.getRect(button);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.space);
      await tester.pump(const Duration(milliseconds: 80));
      expect(tester.getRect(button), original);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.space);
      final gesture = await tester.startGesture(tester.getCenter(button));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(tester.getRect(button).width, lessThan(original.width));
      await gesture.moveBy(const Offset(0, 40));
      await tester.pumpAndSettle();
      expect(tester.getRect(button).width, closeTo(original.width, .1));
      await gesture.cancel();
    },
  );

  testWidgets(
    'enabling reduced motion during a held press resets immediately',
    (tester) async {
      await tester.pumpWidget(host());
      final button = find.byType(TextButton);
      final original = tester.getRect(button);
      final held = await tester.startGesture(tester.getCenter(button));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(tester.getRect(button).width, lessThan(original.width));
      await tester.pumpWidget(host(reduced: true));
      expect(tester.getRect(button), original);
      await held.up();
      await tester.pump(const Duration(milliseconds: 60));
      expect(tester.getRect(button), original);
      expect(tester.takeException(), isNull);
    },
  );
}
