import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xjicloud_mobile/main.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('native platform view performs capability handshake', (
    tester,
  ) async {
    await tester.pumpWidget(const XjiCloudProbeApp());
    for (var i = 0; i < 60 && find.text('检查设备能力…').evaluate().isNotEmpty; i++) {
      await tester.pump(const Duration(milliseconds: 250));
    }
    expect(find.text('检查设备能力…'), findsNothing);
    expect(find.text('打开本机模型'), findsOneWidget);
    // This smoke test proves platform wiring only; it never claims PLY/SPZ rendering.
    if (Platform.version.contains('x64')) {
      expect(find.textContaining('arm64-v8a 设备'), findsWidgets);
    }
  });
}
