import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/presentation/shell/adaptive_content.dart';

void main() {
  for (final entry in <double, String>{
    360: 'phone',
    599: 'phone',
    600: 'narrow',
    800: 'narrow',
    839: 'narrow',
    840: 'wide',
    1280: 'wide',
  }.entries) {
    testWidgets('${entry.key}dp selects ${entry.value}', (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = Size(entry.key, 800);
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          home: AdaptiveContent(builder: (context, size) => Text(size.name)),
        ),
      );
      expect(find.text(entry.value), findsOneWidget);
    });
  }
  testWidgets('resize retains caller state and limits wide content to 1280', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    addTearDown(tester.view.reset);
    await tester.pumpWidget(const MaterialApp(home: _Counter()));
    await tester.tap(find.text('count 0'));
    await tester.pump();
    tester.view.physicalSize = const Size(1600, 800);
    await tester.pump();
    expect(find.text('count 1'), findsOneWidget);
    expect(tester.getSize(find.byKey(const Key('content'))).width, 1280);
  });
}

class _Counter extends StatefulWidget {
  const _Counter();
  @override
  State<_Counter> createState() => _CounterState();
}

class _CounterState extends State<_Counter> {
  int count = 0;
  @override
  Widget build(BuildContext context) => AdaptiveContent(
    builder: (context, size) => SizedBox(
      key: const Key('content'),
      width: double.infinity,
      child: TextButton(
        onPressed: () => setState(() => count++),
        child: Text('count $count'),
      ),
    ),
  );
}
