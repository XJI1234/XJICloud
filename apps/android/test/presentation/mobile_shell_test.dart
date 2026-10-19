import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:xjicloud_mobile/app/app.dart';
import 'package:xjicloud_mobile/app/router.dart';

GoRouter _router() => createAppRouter(
  initialLocation: '/home',
  routes: [
    createMobileShellRoute(
      destinations: ShellDestinations(
        home: (_, _) => _list('home'),
        projects: (_, _) => _list('projects'),
        viewer: (_, _) => _list('viewer'),
        account: (_, _) => _list('account'),
      ),
      onUpload: (context) => context.push('/upload'),
    ),
    GoRoute(
      path: '/upload',
      builder: (_, _) => Scaffold(
        appBar: AppBar(title: const Text('upload flow')),
        body: const Text('fixed target'),
      ),
    ),
  ],
);

Widget _list(String id) => ListView.builder(
  key: PageStorageKey(id),
  itemExtent: 60,
  itemCount: 100,
  itemBuilder: (_, index) => Text('$id $index'),
);

void main() {
  testWidgets(
    'system back closes flow before returning a destination to home',
    (tester) async {
      final router = _router();
      addTearDown(router.dispose);
      await tester.pumpWidget(
        XjiCloudApp(router: router, locale: const Locale('zh')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('我的'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('上传'));
      await tester.pumpAndSettle();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('account 0'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('home 0'), findsOneWidget);
    },
  );

  testWidgets(
    'four destinations are ordered and upload returns to its source',
    (tester) async {
      final router = _router();
      addTearDown(router.dispose);
      await tester.pumpWidget(
        XjiCloudApp(router: router, locale: const Locale('zh')),
      );
      await tester.pumpAndSettle();
      final labels = ['主页', '工程管理', '模型查看', '我的'];
      for (var i = 1; i < labels.length; i++) {
        expect(
          tester.getCenter(find.text(labels[i])).dx,
          greaterThan(tester.getCenter(find.text(labels[i - 1])).dx),
        );
      }
      await tester.tap(find.text('工程管理'));
      await tester.pumpAndSettle();
      expect(find.text('projects 0'), findsOneWidget);
      await tester.tap(find.byTooltip('上传'));
      await tester.pumpAndSettle();
      expect(find.text('upload flow'), findsOneWidget);
      router.pop();
      await tester.pumpAndSettle();
      expect(find.text('projects 0'), findsOneWidget);
      for (final label in ['模型查看', '我的', '主页']) {
        await tester.tap(find.text(label));
        await tester.pumpAndSettle();
      }
      expect(find.text('home 0'), findsOneWidget);
    },
  );

  testWidgets('returning to a destination preserves its independent scroll', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    addTearDown(tester.view.reset);
    final router = _router();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      XjiCloudApp(router: router, locale: const Locale('zh')),
    );
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView).first, const Offset(0, -900));
    await tester.pumpAndSettle();
    final before = tester.getTopLeft(find.text('home 16')).dy;
    await tester.tap(find.text('工程管理'));
    await tester.pumpAndSettle();
    expect(find.text('projects 0'), findsOneWidget);
    await tester.tap(find.text('主页'));
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.text('home 16')).dy, before);
    tester.view.physicalSize = const Size(1280, 800);
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.text('home 16')).dy, before);
  });

  testWidgets('English navigation fits a 360dp window at 200 percent text', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    addTearDown(tester.view.reset);
    final router = _router();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      XjiCloudApp(
        router: router,
        locale: const Locale('en'),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(2)),
          child: child!,
        ),
      ),
    );
    await tester.pumpAndSettle();
    for (final label in ['Home', 'Projects', 'Model viewer', 'Account']) {
      expect(find.text(label), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });

  for (final size in [
    const Size(360, 800),
    const Size(800, 360),
    const Size(800, 1280),
    const Size(1280, 800),
  ]) {
    testWidgets('$size bottom navigation fits 200 percent text and safe area', (
      tester,
    ) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = size;
      tester.view.padding = FakeViewPadding(bottom: 24);
      addTearDown(tester.view.reset);
      final router = _router();
      addTearDown(router.dispose);
      await tester.pumpWidget(
        XjiCloudApp(
          router: router,
          locale: const Locale('zh'),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(2)),
            child: child!,
          ),
        ),
      );
      await tester.pumpAndSettle();
      for (final label in ['主页', '工程管理', '模型查看', '我的']) {
        expect(find.text(label), findsOneWidget);
        expect(
          tester.getBottomLeft(find.text(label)).dy,
          lessThan(size.height - 24),
        );
      }
      expect(tester.takeException(), isNull);
    });
  }
}
