import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xjicloud_mobile/app/app.dart';
import 'package:xjicloud_mobile/app/router.dart';
import 'package:xjicloud_mobile/presentation/widgets/app_sheet.dart';

// Test-only destinations. Never imported by either development/product entry.
Widget _destination(String id) => ListView.builder(
  key: PageStorageKey(id),
  itemExtent: 60,
  itemCount: 100,
  itemBuilder: (_, index) => Text('$id $index'),
);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'device shell navigation upload return scroll rotation and system font',
    (tester) async {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.portraitUp,
      ]);
      final router = createAppRouter(
        initialLocation: '/home',
        routes: [
          createMobileShellRoute(
            destinations: ShellDestinations(
              home: (_, _) => _destination('home'),
              projects: (_, _) => _destination('projects'),
              viewer: (_, _) => _destination('viewer'),
              account: (_, _) => _destination('account'),
            ),
            onUpload: (context) async {
              final choice = await showUploadChooser(context: context);
              if (context.mounted && choice != null) {
                context.push('/upload/${choice.name}');
              }
            },
          ),
          GoRoute(
            path: '/upload/:choice',
            builder: (_, state) => Scaffold(
              appBar: AppBar(
                title: Text('fixture ${state.pathParameters['choice']}'),
              ),
              body: const Text('fixture upload body'),
            ),
          ),
        ],
      );
      addTearDown(router.dispose);
      addTearDown(() => SystemChrome.setPreferredOrientations([]));
      await tester.pumpWidget(
        XjiCloudApp(router: router, locale: const Locale('zh')),
      );
      await tester.pumpAndSettle();

      final homeContext = tester.element(find.text('home 0'));
      final size = MediaQuery.sizeOf(homeContext);
      final textSize = MediaQuery.textScalerOf(homeContext).scale(16);
      debugPrint('Task2 device: portrait=$size system 16sp=$textSize');
      expect(size.height, greaterThan(size.width));
      // Optional acceptance mode: run with Android font_scale=2.0 and
      // --dart-define=SHELL_EXPECT_LARGE_FONT=true, then restore system settings.
      // Ordinary test runs still work with the device's own text preference.
      if (const bool.fromEnvironment('SHELL_EXPECT_LARGE_FONT')) {
        expect(textSize, greaterThanOrEqualTo(32));
      }
      for (final label in ['主页', '工程管理', '模型查看', '我的']) {
        expect(find.text(label), findsOneWidget);
      }
      await tester.drag(find.byType(ListView).first, const Offset(0, -900));
      await tester.pumpAndSettle();
      final homePosition = tester.getTopLeft(find.text('home 16')).dy;
      await tester.tap(find.text('工程管理'));
      await tester.pumpAndSettle();
      expect(find.text('projects 0'), findsOneWidget);
      await tester.tap(find.byTooltip('上传'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('上传 PLY/SPZ 模型'));
      await tester.pumpAndSettle();
      expect(find.text('fixture model'), findsOneWidget);
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(find.text('projects 0'), findsOneWidget);
      await tester.tap(find.byTooltip('上传'));
      await tester.pumpAndSettle();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('projects 0'), findsOneWidget);
      for (final entry in {'模型查看': 'viewer 0', '我的': 'account 0'}.entries) {
        await tester.tap(find.text(entry.key));
        await tester.pumpAndSettle();
        expect(find.text(entry.value), findsOneWidget);
      }
      await tester.tap(find.text('主页'));
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.text('home 16')).dy, homePosition);

      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
      ]);
      await Future<void>.delayed(const Duration(seconds: 1));
      await tester.pumpAndSettle();
      final landscape = MediaQuery.sizeOf(tester.element(find.text('主页')));
      debugPrint('Task2 device: landscape=$landscape');
      expect(landscape.width, greaterThan(landscape.height));
      for (final label in ['主页', '工程管理', '模型查看', '我的']) {
        expect(find.text(label), findsOneWidget);
      }
      expect(tester.takeException(), isNull);
    },
  );
}
