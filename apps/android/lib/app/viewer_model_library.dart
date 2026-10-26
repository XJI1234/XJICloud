import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../features/model_asset/domain/model_asset.dart';
import '../features/model_asset/presentation/model_assets_controller.dart';
import '../features/project/presentation/workspace_controller.dart';
import '../shared/l10n/app_localizations.dart';
import '../shared/presentation/error_text.dart';

/// UI composition across existing controllers; no additional repository or API.
class ViewerModelLibrary extends StatefulWidget {
  const ViewerModelLibrary({
    super.key,
    required this.workspace,
    required this.models,
    required this.onOpen,
    required this.onManageProjects,
  });
  final WorkspaceController workspace;
  final ModelAssetsController models;
  final ValueChanged<ModelAsset> onOpen;
  final VoidCallback onManageProjects;
  @override
  State<ViewerModelLibrary> createState() => _ViewerModelLibraryState();
}

class _ViewerModelLibraryState extends State<ViewerModelLibrary> {
  String? selected;
  String? loaded;
  int generation = -1;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([widget.workspace, widget.models]),
    builder: (context, _) {
      final w = widget.workspace,
          m = widget.models,
          l = AppLocalizations.of(context);
      if (generation != w.userGeneration) {
        generation = w.userGeneration;
        selected = loaded = null;
      }
      if (w.projectById(selected) == null) {
        selected =
            w.projectById(w.activeProjectId)?.id ?? w.projects.firstOrNull?.id;
      }
      final id = selected;
      if (id != null && loaded != id) {
        loaded = id;
        final expectedGeneration = generation;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted &&
              expectedGeneration == widget.workspace.userGeneration &&
              selected == id) {
            unawaited(m.refresh(id));
          }
        });
      }
      final error = w.error ?? (id == null ? null : m.listError(id));
      final busy = w.loading || (id != null && m.loading(id));
      final items = id == null ? <ModelAsset>[] : m.modelsFor(id);
      return CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 16, 12, 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          l.viewerCloudTitle,
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ),
                      IconButton(
                        tooltip: l.viewerRefresh,
                        onPressed: busy
                            ? null
                            : () async {
                                if (id == null || w.error != null) {
                                  await w.refresh();
                                } else {
                                  await m.refresh(id);
                                }
                              },
                        icon: const Icon(CupertinoIcons.arrow_clockwise),
                      ),
                    ],
                  ),
                ),
                if (w.projects.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: DropdownButtonFormField<String>(
                      key: ValueKey('viewer-project-$generation-$id'),
                      initialValue: id,
                      isExpanded: true,
                      decoration: InputDecoration(labelText: l.viewerProject),
                      items: [
                        for (final p in w.projects)
                          DropdownMenuItem(
                            value: p.id,
                            child: Text(
                              p.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: (value) => setState(() => selected = value),
                    ),
                  ),
                if (busy)
                  const Padding(
                    padding: EdgeInsets.all(24),
                    child: LinearProgressIndicator(),
                  ),
                if (error != null)
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Semantics(
                      liveRegion: true,
                      child: Text(errorText(l, error)),
                    ),
                  ),
              ],
            ),
          ),
          if (items.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        CupertinoIcons.cube_box,
                        size: 40,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                      const SizedBox(height: 16),
                      Text(
                        busy
                            ? l.loading
                            : id == null
                            ? l.projectEmpty
                            : l.modelEmpty,
                        textAlign: TextAlign.center,
                      ),
                      if (id == null)
                        IconButton(
                          tooltip: l.projects,
                          onPressed: widget.onManageProjects,
                          icon: const Icon(CupertinoIcons.square_grid_2x2),
                        ),
                    ],
                  ),
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.all(16),
              sliver: SliverList.separated(
                itemCount: items.length,
                separatorBuilder: (_, _) => const Divider(indent: 64),
                itemBuilder: (context, index) {
                  final model = items[index];
                  return ListTile(
                    key: ValueKey('viewer-model-${model.id}'),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 8,
                    ),
                    leading: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Icon(
                          CupertinoIcons.cube,
                          color: Theme.of(context)
                              .colorScheme
                              .onPrimaryContainer,
                        ),
                      ),
                    ),
                    title: Text(
                      model.fileName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(
                      '${model.format.toUpperCase()} · ${(model.sizeBytes / 1048576).toStringAsFixed(1)} MB',
                    ),
                    trailing: const Icon(
                      CupertinoIcons.chevron_right,
                      size: 16,
                    ),
                    onTap: () => widget.onOpen(model),
                  );
                },
              ),
            ),
        ],
      );
    },
  );
}
