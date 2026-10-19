import 'package:flutter/material.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../domain/upload_checkpoint_port.dart';
import 'upload_controller.dart';
import 'training_job_panel.dart';

class UploadFlowPage extends StatefulWidget {
  const UploadFlowPage({
    super.key,
    required this.controller,
    required this.projectId,
    required this.projectName,
  });
  final UploadController controller;
  final String projectId, projectName;
  @override
  State<UploadFlowPage> createState() => _UploadFlowPageState();
}

class _UploadFlowPageState extends State<UploadFlowPage> {
  final name = TextEditingController();
  late final generation = widget.controller.userGeneration;
  bool closing = false, asking = false;
  @override
  void initState() {
    super.initState();
    name.text = widget.controller.checkpoint?.name ?? '';
    widget.controller.addListener(changed);
  }

  void changed() {
    if (mounted && generation != widget.controller.userGeneration && !closing) {
      closing = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) Navigator.of(context).pop();
      });
    }
  }

  Future<void> leave() async {
    if (closing || asking) return;
    asking = true;
    final c = widget.controller, l = AppLocalizations.of(context);
    final dirty = c.busy || c.draft != null || c.checkpoint != null;
    final confirmed =
        !dirty ||
        await confirmTrainingAction(
          context,
          l.datasetLeave,
          l.datasetLeaveMessage,
        );
    asking = false;
    if (confirmed && mounted) {
      c.pause();
      setState(() => closing = true);
      Navigator.pop(context);
    }
  }

  Future<void> confirm(
    String title,
    String message,
    Future<void> Function() action,
  ) async {
    final c = widget.controller, g = c.userGeneration;
    if (await confirmTrainingAction(context, title, message) &&
        g == c.userGeneration) {
      await action();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(changed);
    name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller,
          l = AppLocalizations.of(context),
          draft = c.draft,
          state = c.checkpoint;
      return PopScope(
        canPop: closing,
        onPopInvokedWithResult: (didPop, result) {
          if (!didPop) leave();
        },
        child: Scaffold(
          appBar: AppBar(
            leading: BackButton(onPressed: leave),
            title: Text(l.uploadImages),
          ),
          body: SafeArea(
            child: Align(
              alignment: Alignment.topCenter,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 840),
                child: ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    Text(
                      '${l.uploadDestination}: ${widget.projectName}',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    SelectableText(widget.projectId),
                    const SizedBox(height: 12),
                    Text(l.datasetPolicy),
                    if (c.error case final error?)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: Semantics(
                          liveRegion: true,
                          child: Text(errorText(l, error)),
                        ),
                      ),
                    if (c.restoring) Text(l.loading),
                    if (c.busy) Text(l.loading),
                    if (draft == null && !c.restoring)
                      Wrap(
                        spacing: 12,
                        runSpacing: 8,
                        children: [
                          FilledButton.icon(
                            key: const Key('choose-dataset-images'),
                            onPressed: c.busy
                                ? null
                                : () => c.choose(widget.projectId),
                            icon: const Icon(Icons.photo_library_outlined),
                            label: Text(l.chooseImages),
                          ),
                          OutlinedButton.icon(
                            key: const Key('choose-dataset-folder'),
                            onPressed: c.busy
                                ? null
                                : () => c.choose(
                                    widget.projectId,
                                    directory: true,
                                  ),
                            icon: const Icon(Icons.folder_open_outlined),
                            label: Text(l.chooseImageFolder),
                          ),
                        ],
                      ),
                    if (draft != null) ...[
                      const SizedBox(height: 20),
                      Text(
                        l.datasetPreview,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      Text('${l.datasetImageCount}: ${draft.files.length}'),
                      Text('${l.datasetTotalBytes}: ${draft.totalBytes} B'),
                      for (final file in draft.files.take(8))
                        Text(
                          '${file.archivedName} ← ${file.source.relativeName} (${file.source.sizeBytes} B)',
                        ),
                      const SizedBox(height: 16),
                      TextField(
                        key: const Key('dataset-name-input'),
                        controller: name,
                        enabled: !c.busy && state == null,
                        maxLength: 256,
                        decoration: InputDecoration(labelText: l.datasetName),
                      ),
                      if (state == null)
                        FilledButton(
                          key: const Key('submit-dataset'),
                          onPressed: c.busy ? null : () => c.submit(name.text),
                          child: Text(l.uploadImages),
                        ),
                    ],
                    if (state != null) ...[
                      if (state.jobId != null)
                        SelectableText('${l.jobId}: ${state.jobId}'),
                      if (c.totalBytes > 0)
                        LinearProgressIndicator(
                          value: (c.sentBytes / c.totalBytes).clamp(0, 1),
                          semanticsLabel: l.datasetTotalBytes,
                          semanticsValue: '${c.sentBytes}/${c.totalBytes}',
                        ),
                      Text('${c.sentBytes}/${c.totalBytes} B'),
                      if (c.paused) Text(l.datasetPaused),
                      Wrap(
                        spacing: 12,
                        runSpacing: 8,
                        children: [
                          if (c.busy)
                            TextButton(
                              onPressed: c.pause,
                              child: Text(l.pauseTransfer),
                            )
                          else if (state.jobId != null &&
                              state.phase != DatasetUploadPhase.completed) ...[
                            FilledButton(
                              onPressed: () => c.resume(),
                              child: Text(l.datasetResume),
                            ),
                            OutlinedButton(
                              onPressed: () => confirm(
                                l.datasetRestart,
                                l.datasetRestartMessage,
                                c.restart,
                              ),
                              child: Text(l.datasetRestart),
                            ),
                            if (c.error?.code == 'completion_uncertain')
                              TextButton(
                                onPressed: () => confirm(
                                  l.datasetCompleteRetry,
                                  l.datasetCompleteMessage,
                                  () => c.resume(retryCompletion: true),
                                ),
                                child: Text(l.datasetCompleteRetry),
                              ),
                          ],
                          if (state.jobId == null && !c.busy)
                            TextButton(
                              onPressed: () => confirm(
                                l.datasetNoCandidate,
                                l.datasetNoCandidateMessage,
                                c.confirmNoCreatedJob,
                              ),
                              child: Text(l.datasetNoCandidate),
                            ),
                        ],
                      ),
                      if (state.jobId == null)
                        for (final job in c.jobsFor(widget.projectId))
                          Card(
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(job.name),
                                  SelectableText(job.id),
                                  Text(
                                    job.createdAt.toLocal().toIso8601String(),
                                  ),
                                  Text(jobStatusText(l, job.status)),
                                  TextButton(
                                    onPressed: c.busy
                                        ? null
                                        : () => confirm(
                                            l.datasetConfirmCandidate,
                                            l.datasetCandidateMessage,
                                            () => c.resolveCandidate(job.id),
                                          ),
                                    child: Text(l.datasetConfirmCandidate),
                                  ),
                                ],
                              ),
                            ),
                          ),
                    ],
                    if (draft != null || state != null)
                      Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton(
                          onPressed: c.busy
                              ? null
                              : () => confirm(
                                  state?.phase == DatasetUploadPhase.completed
                                      ? l.finishUpload
                                      : l.datasetDiscard,
                                  state?.phase == DatasetUploadPhase.completed
                                      ? l.uploadCompletedStorage
                                      : l.datasetDiscardMessage,
                                  () async {
                                    await c.discard();
                                  },
                                ),
                          child: Text(
                            state?.phase == DatasetUploadPhase.completed
                                ? l.finishUpload
                                : l.datasetDiscard,
                          ),
                        ),
                      ),
                    TrainingJobPanel(
                      controller: c,
                      projectId: widget.projectId,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    },
  );
}
