import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/main.dart';

void main() {
  testWidgets(
    'development entry explains device limitation and has no product navigation',
    (tester) async {
      await tester.pumpWidget(const XjiCloudProbeApp());
      expect(find.text('Native3DGS · P0 开发验证'), findsOneWidget);
      expect(find.textContaining('未完成真机渲染验收'), findsOneWidget);
      expect(find.text('打开本机模型'), findsOneWidget);
    },
  );
}
