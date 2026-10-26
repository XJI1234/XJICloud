import 'package:flutter/material.dart';

import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import 'model_assets_controller.dart';

class CachePage extends StatefulWidget {
  const CachePage({super.key, required this.controller});
  final ModelAssetsController controller;
  @override
  State<CachePage> createState() => _CachePageState();
}

class _CachePageState extends State<CachePage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.controller.refreshCache();
    });
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller, l = AppLocalizations.of(context);
      return Scaffold(
        appBar: AppBar(title: Text(l.cacheTitle)),
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 640),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(l.cacheDescription),
                    const SizedBox(height: 24),
                    Text('${l.cacheBytes}: ${c.cacheBytes}'),
                    Text('${l.cacheFreed}: ${c.freedBytes}'),
                    for (final entry in c.cacheEntries)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Text(entry.fileName),
                              Text('${entry.sizeBytes} B'),
                              SelectableText(entry.modelId),
                              if (entry.inUse) Text(l.cacheInUse),
                              TextButton(
                                key: ValueKey('evict-cache-${entry.key}'),
                                onPressed: c.cacheBusy || entry.inUse
                                    ? null
                                    : () => c.evictCache(entry),
                                child: Text(
                                  l.cacheRemove,
                                  semanticsLabel:
                                      '${l.cacheRemove}: ${entry.fileName}',
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    if (c.error case final error?)
                      Semantics(
                        liveRegion: true,
                        child: Text(errorText(l, error)),
                      ),
                    const SizedBox(height: 16),
                    FilledButton(
                      key: const Key('clear-model-cache'),
                      onPressed: c.cacheBusy ? null : c.clearCache,
                      child: Text(c.cacheBusy ? l.loading : l.cacheClear),
                    ),
                    TextButton(
                      onPressed: c.cacheBusy ? null : c.refreshCache,
                      child: Text(l.retry),
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
