import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import 'project_detail_page.dart';
import 'workspace_controller.dart';

class ProjectListPage extends StatelessWidget {
  const ProjectListPage({
    super.key,
    required this.controller,
    this.projectId,
    this.modelSectionBuilder,
  });
  final WorkspaceController controller;
  final String? projectId;
  final Widget Function(String projectId)? modelSectionBuilder;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final l = AppLocalizations.of(context);
      return LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= 840;
          final selected = projectId ?? controller.selectedProjectId;
          final detail = selected == null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Text(l.selectProject),
                  ),
                )
              : ProjectDetailPage(
                  controller: controller,
                  projectId: selected,
                  modelSectionBuilder: modelSectionBuilder,
                );
          final list = ListView(
            padding: const EdgeInsets.all(20),
            children: [
              if (controller.loading)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(l.loading),
                ),
              if (controller.error case final error?)
                Semantics(liveRegion: true, child: Text(errorText(l, error))),
              if (controller.projects.isEmpty &&
                  !controller.loading &&
                  controller.error == null) ...[
                Text(l.projectEmpty),
                const SizedBox(height: 8),
                Text(l.projectsIntro),
              ],
              for (final p in controller.projects)
                Card(
                  child: ListTile(
                    key: Key('project-${p.id}'),
                    leading: const Icon(CupertinoIcons.folder),
                    selected: p.id == selected,
                    title: Text(p.name),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (p.description.isNotEmpty) Text(p.description),
                        Text(
                          '${l.projectCreated}: ${DateFormat.yMMMd(l.localeName).format(p.createdAt.toLocal())}',
                        ),
                        if (p.id == controller.activeProjectId)
                          Text(l.currentProject),
                      ],
                    ),
                    onTap: () {
                      controller.select(p.id);
                      context.go('/projects/${p.id}');
                    },
                  ),
                ),
              const SizedBox(height: 16),
              FilledButton.icon(
                key: const Key('create-project'),
                onPressed: controller.busy
                    ? null
                    : () => showProjectEditor(context, controller),
                icon: const Icon(Icons.add),
                label: Text(l.createProject),
              ),
              TextButton(
                onPressed: controller.loading || controller.busy
                    ? null
                    : controller.refresh,
                child: Text(l.retry),
              ),
            ],
          );
          return Scaffold(
            appBar: AppBar(
              leading: !wide && projectId != null
                  ? BackButton(onPressed: () => context.go('/projects'))
                  : null,
              title: Text(l.projects),
            ),
            body: SafeArea(
              child: Align(
                alignment: Alignment.topCenter,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 1280),
                  child: wide
                      ? Row(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            SizedBox(width: 300, child: list),
                            const VerticalDivider(width: 24),
                            Expanded(child: detail),
                          ],
                        )
                      : projectId != null
                      ? detail
                      : list,
                ),
              ),
            ),
          );
        },
      );
    },
  );
}
