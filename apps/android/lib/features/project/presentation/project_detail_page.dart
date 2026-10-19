import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../domain/project.dart';
import 'workspace_controller.dart';

class ProjectDetailPage extends StatelessWidget {
  const ProjectDetailPage({
    super.key,
    required this.controller,
    required this.projectId,
    this.modelSectionBuilder,
  });
  final WorkspaceController controller;
  final String projectId;
  final Widget Function(String projectId)? modelSectionBuilder;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final l = AppLocalizations.of(context);
      final p = controller.projectById(projectId);
      return ListView(
        key: Key('project-detail-$projectId'),
        padding: const EdgeInsets.all(20),
        children: [
          if (controller.loading) Text(l.loading),
          if (controller.error case final error?)
            Semantics(liveRegion: true, child: Text(errorText(l, error))),
          if (p == null) ...[
            if (!controller.loading) Text(l.projectUnavailable),
            TextButton(
              onPressed: controller.loading || controller.busy
                  ? null
                  : controller.refresh,
              child: Text(l.retry),
            ),
          ] else ...[
            Text(p.name, style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 12),
            if (p.description.isNotEmpty) SelectableText(p.description),
            const SizedBox(height: 12),
            Text(
              '${l.projectCreated}: ${DateFormat.yMMMd(l.localeName).format(p.createdAt.toLocal())}',
            ),
            const SizedBox(height: 24),
            if (p.id == controller.activeProjectId)
              Text(l.currentProject)
            else
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton(
                  key: const Key('set-current-project'),
                  onPressed: controller.busy
                      ? null
                      : () => controller.openProject(p.id),
                  child: Text(l.setCurrentProject),
                ),
              ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                OutlinedButton(
                  key: const Key('edit-project'),
                  onPressed: controller.busy
                      ? null
                      : () =>
                            showProjectEditor(context, controller, project: p),
                  child: Text(l.editProject),
                ),
                TextButton(
                  key: const Key('delete-project'),
                  style: TextButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.error,
                  ),
                  onPressed: controller.busy
                      ? null
                      : () async {
                          final generation = controller.userGeneration;
                          final confirmed = await showDialog<bool>(
                            context: context,
                            useRootNavigator: false,
                            builder: (context) => AlertDialog(
                              title: Text('${l.deleteProject}: ${p.name}'),
                              content: Text(l.deleteProjectMessage),
                              actions: [
                                TextButton(
                                  onPressed: () =>
                                      Navigator.pop(context, false),
                                  child: Text(l.cancel),
                                ),
                                FilledButton(
                                  onPressed: () => Navigator.pop(context, true),
                                  child: Text(l.deleteProject),
                                ),
                              ],
                            ),
                          );
                          if (confirmed == true &&
                              generation == controller.userGeneration) {
                            await controller.deleteProject(p.id);
                          }
                        },
                  child: Text(l.deleteProject),
                ),
              ],
            ),
            if (controller.busy)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(l.loading),
              ),
            if (modelSectionBuilder != null) modelSectionBuilder!(p.id),
          ],
        ],
      );
    },
  );
}

Future<void> showProjectEditor(
  BuildContext context,
  WorkspaceController controller, {
  Project? project,
}) => MediaQuery.sizeOf(context).width < 840
    ? showModalBottomSheet<void>(
        context: context,
        useRootNavigator: false,
        useSafeArea: true,
        isScrollControlled: true,
        isDismissible: false,
        enableDrag: false,
        builder: (context) => _ProjectEditor(
          controller: controller,
          project: project,
          sheet: true,
        ),
      )
    : showDialog<void>(
        context: context,
        useRootNavigator: false,
        barrierDismissible: false,
        builder: (context) =>
            _ProjectEditor(controller: controller, project: project),
      );

class _ProjectEditor extends StatefulWidget {
  const _ProjectEditor({
    required this.controller,
    this.project,
    this.sheet = false,
  });
  final WorkspaceController controller;
  final Project? project;
  final bool sheet;
  @override
  State<_ProjectEditor> createState() => _ProjectEditorState();
}

class _ProjectEditorState extends State<_ProjectEditor> {
  late final name = TextEditingController(text: widget.project?.name ?? '');
  late final description = TextEditingController(
    text: widget.project?.description ?? '',
  );
  late final generation = widget.controller.userGeneration;
  final form = GlobalKey<FormState>();
  bool get dirty =>
      name.text != (widget.project?.name ?? '') ||
      description.text != (widget.project?.description ?? '');
  bool closing = false;
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(changed);
  }

  void changed() {
    if (!mounted) return;
    if (generation != widget.controller.userGeneration && !closing) {
      closing = true;
      Navigator.of(context).pop();
    } else {
      setState(() {});
    }
  }

  Future<void> leave() async {
    if (widget.controller.busy || closing) return;
    final l = AppLocalizations.of(context);
    if (dirty) {
      final discard = await showDialog<bool>(
        context: context,
        useRootNavigator: false,
        builder: (context) => AlertDialog(
          title: Text(l.discardChanges),
          content: Text(l.discardChangesMessage),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(l.keepEditing),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(l.discard),
            ),
          ],
        ),
      );
      if (discard != true || !mounted) return;
    }
    closing = true;
    Navigator.pop(context);
  }

  @override
  void dispose() {
    widget.controller.removeListener(changed);
    name.dispose();
    description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final c = widget.controller;
    return PopScope(
      canPop: closing,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) leave();
      },
      child: _EditorSurface(
        sheet: widget.sheet,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: form,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    widget.project == null ? l.createProject : l.editProject,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 20),
                  TextFormField(
                    key: const Key('project-name-input'),
                    controller: name,
                    enabled: !c.busy,
                    decoration: InputDecoration(labelText: l.projectName),
                    textInputAction: TextInputAction.next,
                    maxLength: 256,
                    validator: (value) => value == null || value.trim().isEmpty
                        ? l.requiredField
                        : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    key: const Key('project-description-input'),
                    controller: description,
                    enabled: !c.busy,
                    decoration: InputDecoration(
                      labelText: l.projectDescription,
                    ),
                    minLines: 2,
                    maxLines: 5,
                    maxLength: 2000,
                  ),
                  if (c.error case final error?)
                    Padding(
                      padding: const EdgeInsets.only(top: 12),
                      child: Semantics(
                        liveRegion: true,
                        child: Text(errorText(l, error)),
                      ),
                    ),
                  const SizedBox(height: 20),
                  Wrap(
                    alignment: WrapAlignment.end,
                    spacing: 12,
                    runSpacing: 8,
                    children: [
                      TextButton(
                        onPressed: c.busy ? null : leave,
                        child: Text(l.cancel),
                      ),
                      FilledButton(
                        key: const Key('save-project'),
                        onPressed: c.busy
                            ? null
                            : () async {
                                if (!form.currentState!.validate()) return;
                                final saved = await c.save(
                                  name.text,
                                  description.text,
                                  id: widget.project?.id,
                                );
                                if (saved &&
                                    mounted &&
                                    generation == c.userGeneration) {
                                  closing = true;
                                  Navigator.pop(this.context);
                                }
                              },
                        child: Text(c.busy ? l.loading : l.saveProject),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _EditorSurface extends StatelessWidget {
  const _EditorSurface({required this.sheet, required this.child});
  final bool sheet;
  final Widget child;
  @override
  Widget build(BuildContext context) => sheet
      ? Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.viewInsetsOf(context).bottom,
          ),
          child: child,
        )
      : Dialog(child: child);
}
