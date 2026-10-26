import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/app/app.dart';
import 'package:xjicloud_mobile/app/router.dart';
import 'package:xjicloud_mobile/app/session_coordinator.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/training_job_panel.dart';
import 'package:xjicloud_mobile/features/dataset_training/presentation/upload_controller.dart';
import 'package:xjicloud_mobile/shared/domain/cancellation.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

import '../identity/session_policy_test.dart' show MemoryStore, session;
import 'job_policy_test.dart' show Events;
import 'submit_dataset_test.dart' show Jobs, Journal, Storage, Sources;

class _CountedJobs extends Jobs {
  int lists = 0;
  @override
  Future<Result<List<TrainingJob>>> list(String project, int generation) {
    lists++;
    return super.list(project, generation);
  }
}

class _CountedEvents extends Events {
  final connections = <Cancellation>[];
  int get active => connections.where((c) => !c.isCancelled).length;
  @override
  Stream<JobProgress> subscribe(String id, int generation, Cancellation c) {
    connections.add(c);
    return super.subscribe(id, generation, c);
  }
}

void main() {
  for (final summary in [false, true]) {
    for (final degraded in [false, true]) {
      testWidgets(
        'indexed shell hides ${summary ? 'Home summary' : 'project panel'} and stops ${degraded ? 'degraded' : 'healthy'} observation, returning reconciles once',
        (tester) async {
          final sessions = SessionCoordinator(MemoryStore());
          await sessions.activate(session());
          final repo = _CountedJobs()..status = JobStatus.queued;
          final events = _CountedEvents();
          final c = UploadController(
            repo,
            Sources(),
            Storage(),
            Journal(),
            events,
            sessions,
          )..synchronize();
          Widget panel() => SingleChildScrollView(
            child: TrainingJobPanel(
              controller: c,
              projectId: 'p',
              summary: summary,
            ),
          );
          final branch = summary ? 'home' : 'projects';
          final router = createAppRouter(
            initialLocation: '/$branch',
            routes: [
              createMobileShellRoute(
                destinations: ShellDestinations(
                  home: (_, _) => summary ? panel() : const Text('Home'),
                  projects: (_, _) =>
                      summary ? const Text('Projects') : panel(),
                  viewer: (_, _) => const Text('Viewer'),
                  account: (_, _) => const Text('Account'),
                ),
                onUpload: (_) {},
              ),
            ],
          );
          try {
            await tester.pumpWidget(
              XjiCloudApp(router: router, locale: const Locale('en')),
            );
            await tester.pumpAndSettle();
            expect(repo.gets, 1);
            expect(repo.lists, 1);
            expect(events.connections.length, 1);
            expect(events.active, 1);
            if (degraded) {
              events.streams.last.addError(StateError('disconnect'));
              await tester.pumpAndSettle();
            }
            await tester.tap(find.byKey(const Key('nav-account')));
            await tester.pumpAndSettle();
            final beforeGets = repo.gets, beforeLists = repo.lists;
            expect(events.active, 0);
            await tester.pump(const Duration(seconds: 30));
            await tester.pump();
            expect(repo.gets, beforeGets);
            expect(repo.lists, beforeLists);
            repo.status = JobStatus.running;
            await tester.tap(find.byKey(Key('nav-$branch')));
            await tester.pumpAndSettle();
            expect(repo.gets, beforeGets + 1);
            expect(repo.lists, beforeLists + 1);
            expect(events.connections.length, 2);
            expect(events.active, 1);
            expect(c.jobsFor('p').single.status, JobStatus.running);
            expect(find.text('Running'), findsOneWidget);
            expect(tester.takeException(), isNull);
          } finally {
            await tester.pumpWidget(const SizedBox());
            router.dispose();
            c.dispose();
            sessions.dispose();
          }
        },
      );
    }
  }

  testWidgets(
    'hidden branch releases only its consumer while a visible panel keeps the deduplicated watcher',
    (tester) async {
      final sessions = SessionCoordinator(MemoryStore());
      await sessions.activate(session());
      final repo = _CountedJobs(), events = _CountedEvents();
      final c = UploadController(
        repo,
        Sources(),
        Storage(),
        Journal(),
        events,
        sessions,
      )..synchronize();
      final showRoot = ValueNotifier(true);
      final router = createAppRouter(
        initialLocation: '/projects',
        routes: [
          createMobileShellRoute(
            destinations: ShellDestinations(
              home: (_, _) => const Text('Home'),
              projects: (_, _) => SingleChildScrollView(
                child: TrainingJobPanel(controller: c, projectId: 'p'),
              ),
              viewer: (_, _) => const Text('Viewer'),
              account: (_, _) => const Text('Account'),
            ),
            onUpload: (_) {},
          ),
        ],
      );
      try {
        await tester.pumpWidget(
          XjiCloudApp(
            router: router,
            locale: const Locale('en'),
            builder: (_, child) => ValueListenableBuilder(
              valueListenable: showRoot,
              builder: (_, visible, _) => Column(
                children: [
                  if (visible)
                    SizedBox(
                      height: 220,
                      child: SingleChildScrollView(
                        child: TrainingJobPanel(
                          controller: c,
                          projectId: 'p',
                          summary: true,
                        ),
                      ),
                    ),
                  Expanded(child: child!),
                ],
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(repo.gets, 1);
        expect(events.connections.length, 1);
        await tester.tap(find.byKey(const Key('nav-account')));
        await tester.pumpAndSettle();
        expect(events.active, 1);
        await tester.pump(const Duration(seconds: 15));
        await tester.pump();
        expect(repo.gets, 2);
        expect(events.connections.length, 1);
        showRoot.value = false;
        await tester.pumpAndSettle();
        expect(events.active, 0);
        final before = repo.gets;
        await tester.pump(const Duration(seconds: 30));
        expect(repo.gets, before);
        expect(tester.takeException(), isNull);
      } finally {
        await tester.pumpWidget(const SizedBox());
        router.dispose();
        showRoot.dispose();
        c.dispose();
        sessions.dispose();
      }
    },
  );
}
