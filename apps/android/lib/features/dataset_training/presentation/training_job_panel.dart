import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../domain/training_job.dart';
import '../domain/training_output_port.dart';
import '../domain/job_policy.dart';
import 'upload_controller.dart';

String jobStatusText(AppLocalizations l, JobStatus status) => switch (status) {
  JobStatus.pending => l.jobPending,
  JobStatus.uploading => l.jobUploading,
  JobStatus.queued => l.jobQueued,
  JobStatus.running => l.jobRunning,
  JobStatus.completed => l.jobCompleted,
  JobStatus.failed => l.jobFailed,
  JobStatus.cancelled => l.jobCancelled,
};

Future<bool> confirmTrainingAction(
  BuildContext context,
  String title,
  String message,
) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SingleChildScrollView(child: Text(message)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppLocalizations.of(context).cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(title),
          ),
        ],
      ),
    ) ??
    false;

class TrainingJobPanel extends StatefulWidget {
  const TrainingJobPanel({
    super.key,
    required this.controller,
    required this.projectId,
    this.onUpload,
    this.onViewOutput,
    this.summary = false,
  });
  final UploadController controller;
  final String projectId;
  final VoidCallback? onUpload;
  final void Function(TrainingOutputRef)? onViewOutput;
  final bool summary;
  @override
  State<TrainingJobPanel> createState() => _TrainingJobPanelState();
}

class _TrainingJobPanelState extends State<TrainingJobPanel> {
  void Function()? release;
  int generation = -1;
  bool visible = true;
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(identityChanged);
  }

  void identityChanged() {
    if (generation != widget.controller.userGeneration) {
      schedulePresence();
    }
  }

  void schedulePresence() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) syncPresence();
    });
  }

  void syncPresence() {
    if (generation != widget.controller.userGeneration || !visible) {
      release?.call();
      release = null;
    }
    generation = widget.controller.userGeneration;
    if (visible && release == null && widget.controller.userId != null) {
      release = widget.controller.retainProject(widget.projectId);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    visible = ModalRoute.isCurrentOf(context) ?? true;
    syncPresence();
  }

  @override
  void didUpdateWidget(TrainingJobPanel old) {
    super.didUpdateWidget(old);
    if (old.projectId != widget.projectId ||
        old.controller != widget.controller) {
      old.controller.removeListener(identityChanged);
      release?.call();
      release = null;
      widget.controller.addListener(identityChanged);
      syncPresence();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(identityChanged);
    release?.call();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l = AppLocalizations.of(context), c = widget.controller;
      final jobs = c.jobsFor(widget.projectId);
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 24),
          Text(l.trainingJobs, style: Theme.of(context).textTheme.titleLarge),
          if (widget.onUpload != null)
            Align(
              alignment: Alignment.centerLeft,
              child: FilledButton.icon(
                key: const Key('project-upload-dataset'),
                onPressed: widget.onUpload,
                icon: const Icon(Icons.add_photo_alternate_outlined),
                label: Text(l.uploadImages),
              ),
            ),
          if (c.loading(widget.projectId)) Text(l.loading),
          if (c.listError(widget.projectId) case final error?)
            Text(errorText(l, error)),
          if (c.error case final error?)
            Semantics(liveRegion: true, child: Text(errorText(l, error))),
          if (jobs.isEmpty &&
              !c.loading(widget.projectId) &&
              c.listError(widget.projectId) == null)
            Text(l.noTrainingJobs),
          for (final job in widget.summary ? jobs.take(3) : jobs)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      job.name,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    SelectableText('${l.jobId}: ${job.id}'),
                    Text(
                      DateFormat.yMMMd(l.localeName)
                          .add_Hm()
                          .format(job.createdAt.toLocal()),
                    ),
                    Text(jobStatusText(l, job.status)),
                    LinearProgressIndicator(
                      value: job.progress / 100,
                      semanticsLabel: jobStatusText(l, job.status),
                      semanticsValue: '${job.progress}%',
                    ),
                    Text('${job.progress}%'),
                    if (job.stage.isNotEmpty) Text(job.stage),
                    if (job.message.isNotEmpty) Text(job.message),
                    if (job.errorMessage case final message?)
                      SelectableText(message),
                    if (job.status == JobStatus.cancelled)
                      Text(l.jobCancelMessage),
                    if (!widget.summary)
                      Wrap(
                        spacing: 12,
                        runSpacing: 8,
                        children: [
                          if (canCancelJob(job.status))
                            TextButton(
                              onPressed: c.mutating(job.id)
                                  ? null
                                  : () async {
                                      final g = c.userGeneration;
                                      if (await confirmTrainingAction(
                                            context,
                                            l.jobCancel,
                                            l.jobCancelMessage,
                                          ) &&
                                          g == c.userGeneration) {
                                        await c.cancelJob(job);
                                      }
                                    },
                              child: Text(l.jobCancel),
                            ),
                          if (canDeleteJob(job.status))
                            TextButton(
                              onPressed: c.mutating(job.id)
                                  ? null
                                  : () async {
                                      final g = c.userGeneration;
                                      if (await confirmTrainingAction(
                                            context,
                                            l.jobDelete,
                                            l.jobDeleteMessage,
                                          ) &&
                                          g == c.userGeneration) {
                                        await c.deleteJob(job);
                                      }
                                    },
                              child: Text(l.jobDelete),
                            ),
                          if (job.status == JobStatus.completed &&
                              c.outputs != null)
                            OutlinedButton(
                              onPressed: c.downloading(job.id)
                                  ? () => c.cancelDownload(job.id)
                                  : () => c.downloadOutput(job),
                              child: Text(
                                c.downloading(job.id)
                                    ? l.cancelDownload
                                    : l.outputDownload,
                              ),
                            ),
                        ],
                      ),
                    if (c.downloading(job.id))
                      Text('${c.outputBytes[job.id] ?? 0} B'),
                    if (c.outputFor(job.id) case final output?) ...[
                      if (widget.onViewOutput != null)
                        OutlinedButton(
                          onPressed: () => widget.onViewOutput!(output),
                          child: Text(l.viewerOutput),
                        ),
                      if (widget.onViewOutput != null) Text(l.viewerOutputNote),
                      Text(
                        '${l.outputDownloaded}: ${output.fileName} (${output.sizeBytes} B)',
                      ),
                    ],
                  ],
                ),
              ),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: c.loading(widget.projectId)
                  ? null
                  : () => c.refresh(widget.projectId),
              child: Text(l.retry),
            ),
          ),
        ],
      );
    },
  );
}
