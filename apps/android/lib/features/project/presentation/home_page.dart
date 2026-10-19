import 'package:flutter/material.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import 'workspace_controller.dart';

class HomePage extends StatelessWidget {
  const HomePage({
    super.key,
    required this.viewModel,
    required this.onCreate,
    required this.onOpen,
    required this.onRetry,
    this.trainingSectionBuilder,
  });
  final HomeViewModel viewModel;
  final VoidCallback onCreate, onRetry;
  final void Function(String) onOpen;
  final Widget Function(String)? trainingSectionBuilder;
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.home)),
      body: SafeArea(
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1000),
            child: ListView(
              padding: const EdgeInsets.all(20),
              children: [
                Text(
                  l.welcome,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 12),
                Text(l.projectsIntro),
                const SizedBox(height: 24),
                if (viewModel.loading)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: Text(l.loading),
                  ),
                if (viewModel.error != null) ...[
                  Semantics(
                    liveRegion: true,
                    child: Text(errorText(l, viewModel.error!)),
                  ),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton(onPressed: onRetry, child: Text(l.retry)),
                  ),
                ],
                Text(
                  l.currentProject,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                if (viewModel.current case final current?)
                  Card(
                    child: ListTile(
                      title: Text(current.name),
                      subtitle: Text(current.description),
                      onTap: () => onOpen(current.id),
                    ),
                  )
                else
                  Text(l.noCurrentProject),
                if (viewModel.current != null && trainingSectionBuilder != null)
                  trainingSectionBuilder!(viewModel.current!.id),
                const SizedBox(height: 24),
                Text(
                  l.recentProjects,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                if (viewModel.recent.isEmpty) Text(l.noRecentProjects),
                for (final project in viewModel.recent)
                  Card(
                    child: ListTile(
                      key: Key('recent-${project.id}'),
                      title: Text(project.name),
                      subtitle: Text(project.description),
                      onTap: () => onOpen(project.id),
                    ),
                  ),
                const SizedBox(height: 24),
                Align(
                  alignment: Alignment.centerLeft,
                  child: FilledButton.icon(
                    key: const Key('create-project'),
                    onPressed: onCreate,
                    icon: const Icon(Icons.add),
                    label: Text(
                      viewModel.hasProjects ||
                              viewModel.loading ||
                              viewModel.error != null
                          ? l.createProject
                          : l.firstProject,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
