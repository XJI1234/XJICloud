import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../domain/upload_checkpoint_port.dart';
import '../domain/model_asset.dart';
import 'model_assets_controller.dart';

class ModelList extends StatefulWidget {
  const ModelList({
    super.key,
    required this.controller,
    required this.projectId,
    this.onView,
  });
  final ModelAssetsController controller;
  final String projectId;
  final void Function(ModelAsset)? onView;
  @override
  State<ModelList> createState() => _ModelListState();
}

class _ModelListState extends State<ModelList> {
  int generation = -1;
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(changed);
    load();
  }

  void load() {
    generation = widget.controller.userGeneration;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.controller.refresh(widget.projectId);
    });
  }

  void changed() {
    if (generation != widget.controller.userGeneration) load();
  }

  @override
  void didUpdateWidget(ModelList old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      old.controller.removeListener(changed);
      widget.controller.addListener(changed);
    }
    if (old.controller != widget.controller ||
        old.projectId != widget.projectId) {
      load();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(changed);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller,
          l = AppLocalizations.of(context),
          project = widget.projectId;
      final items = c.modelsFor(project), generation = c.userGeneration;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 24),
          Text(l.models, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          Text(l.modelUploadPolicy),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              FilledButton.icon(
                key: const Key('upload-model'),
                onPressed: c.uploadBusy || c.restoring || c.checkpoint != null
                    ? null
                    : () => c.chooseAndUpload(project),
                icon: const Icon(Icons.upload_file),
                label: Text(l.uploadModel),
              ),
              TextButton(
                onPressed: c.loading(project) ? null : () => c.refresh(project),
                child: Text(l.retry),
              ),
            ],
          ),
          UploadStatus(controller: c),
          if (c.loading(project)) Text(l.loading),
          if (c.listError(project) case final error?)
            Semantics(liveRegion: true, child: Text(errorText(l, error))),
          if (c.error case final error?)
            Semantics(liveRegion: true, child: Text(errorText(l, error))),
          if (items.isEmpty &&
              !c.loading(project) &&
              c.listError(project) == null)
            Text(l.modelEmpty),
          for (final model in items)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    MergeSemantics(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            model.fileName,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          Text(
                            '${model.format} · ${model.sizeBytes} B · v${model.version}',
                          ),
                          Text(
                            '${l.modelCreated}: ${DateFormat.yMd(l.localeName).add_Hms().format(model.createdAt.toLocal())}',
                          ),
                          Text(
                            '${l.modelUpdated}: ${DateFormat.yMd(l.localeName).add_Hms().format(model.updatedAt.toLocal())}',
                          ),
                          SelectableText(model.id),
                          if (c.cached(model)) Text(l.modelCached),
                          if (c.downloading(model.id))
                            Text(
                              '${c.downloadProgress[model.id] ?? 0} / ${model.sizeBytes} B',
                            ),
                        ],
                      ),
                    ),
                    Wrap(
                      spacing: 12,
                      runSpacing: 8,
                      children: [
                        if (widget.onView != null)
                          OutlinedButton(
                            onPressed: () => widget.onView!(model),
                            child: Text(
                              l.viewerOpen,
                              semanticsLabel:
                                  '${l.viewerOpen}: ${model.fileName}',
                            ),
                          ),
                        if (c.downloading(model.id))
                          TextButton(
                            onPressed: () => c.cancelDownload(model.id),
                            child: Text(
                              l.cancelDownload,
                              semanticsLabel:
                                  '${l.cancelDownload}: ${model.fileName}',
                            ),
                          )
                        else
                          OutlinedButton(
                            onPressed: () => c.download(model),
                            child: Text(
                              l.modelDownload,
                              semanticsLabel:
                                  '${l.modelDownload}: ${model.fileName}',
                            ),
                          ),
                        TextButton(
                          onPressed: c.deleting(model.id)
                              ? null
                              : () async {
                                  if (await confirmModelAction(
                                        context,
                                        l.deleteModel,
                                        model.fileName,
                                        l.deleteModelMessage,
                                      ) &&
                                      generation == c.userGeneration) {
                                    await c.delete(model);
                                  }
                                },
                          child: Text(
                            l.deleteModel,
                            semanticsLabel:
                                '${l.deleteModel}: ${model.fileName}',
                          ),
                        ),
                        if (c.checkpoint case final state?
                            when state.projectId == project &&
                                (state.phase == UploadPhase.uncertain ||
                                    state.phase == UploadPhase.completing) &&
                                state.source.fileName == model.fileName &&
                                state.source.sizeBytes == model.sizeBytes)
                          TextButton(
                            onPressed: c.uploadBusy
                                ? null
                                : () async {
                                    if (await confirmModelAction(
                                          context,
                                          l.reconcileModel,
                                          model.id,
                                          l.reconcileModelMessage,
                                        ) &&
                                        generation == c.userGeneration) {
                                      await c.reconcile(model.id);
                                    }
                                  },
                            child: Text(
                              l.reconcileModel,
                              semanticsLabel:
                                  '${l.reconcileModel}: ${model.fileName} (${model.id})',
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
        ],
      );
    },
  );
}

class UploadStatus extends StatelessWidget {
  const UploadStatus({super.key, required this.controller});
  final ModelAssetsController controller;
  @override
  Widget build(BuildContext context) {
    final c = controller,
        l = AppLocalizations.of(context),
        state = c.checkpoint,
        generation = c.userGeneration;
    if (state == null) {
      return c.uploadBusy
          ? Column(
              children: [
                Text(l.uploadPreparing),
                TextButton(onPressed: c.pause, child: Text(l.cancel)),
              ],
            )
          : const SizedBox.shrink();
    }
    final uncertain =
        state.phase == UploadPhase.uncertain ||
        state.phase == UploadPhase.completing;
    final status = switch (state.phase) {
      UploadPhase.ready => l.uploadReady,
      UploadPhase.paused => l.uploadPaused,
      UploadPhase.completing => l.uploadCompleting,
      UploadPhase.uncertain => l.uploadUncertain,
      UploadPhase.completed => l.uploadCompleted,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(state.source.fileName),
            Text('${l.uploadDestination}: ${state.projectId}'),
            Text(status),
            Text('${state.receivedBytes} / ${state.source.sizeBytes} B'),
            if (uncertain) Text(l.completionUncertain),
            Wrap(
              spacing: 12,
              runSpacing: 8,
              children: [
                if (c.uploadBusy)
                  TextButton(onPressed: c.pause, child: Text(l.pauseTransfer))
                else ...[
                  if (state.model == null && !uncertain)
                    FilledButton(
                      onPressed: () => c.resume(),
                      child: Text(l.resumeTransfer),
                    ),
                  if (state.model == null)
                    TextButton(
                      onPressed: c.reselectSource,
                      child: Text(l.reselectSource),
                    ),
                  if (uncertain)
                    TextButton(
                      onPressed: () async {
                        if (await confirmModelAction(
                              context,
                              l.retryCompletion,
                              state.source.fileName,
                              l.retryCompletionMessage,
                            ) &&
                            generation == c.userGeneration) {
                          await c.resume(retryCompletion: true);
                        }
                      },
                      child: Text(l.retryCompletion),
                    ),
                  TextButton(
                    onPressed: () async {
                      if (state.model != null) {
                        await c.dismissUpload();
                        return;
                      }
                      if (await confirmModelAction(
                            context,
                            l.abandonUpload,
                            state.source.fileName,
                            l.abandonUploadMessage,
                          ) &&
                          generation == c.userGeneration) {
                        await c.dismissUpload();
                      }
                    },
                    child: Text(
                      state.model == null ? l.abandonUpload : l.finishUpload,
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

Future<bool> confirmModelAction(
  BuildContext context,
  String title,
  String identity,
  String message,
) async {
  final l = AppLocalizations.of(context);
  return await showDialog<bool>(
        context: context,
        useRootNavigator: false,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(identity),
                const SizedBox(height: 12),
                Text(message),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(l.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(title),
            ),
          ],
        ),
      ) ??
      false;
}
