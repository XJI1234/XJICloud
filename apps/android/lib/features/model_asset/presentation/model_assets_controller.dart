import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../identity/domain/session_access.dart';
import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/cancellation.dart';
import '../domain/local_file_port.dart';
import '../domain/model_asset.dart';
import '../domain/model_revision.dart';
import '../domain/model_asset_repository.dart';
import '../domain/model_cache_port.dart';
import '../domain/upload_checkpoint_port.dart';
import '../application/upload_model.dart';
import '../application/download_model.dart';
import '../application/delete_model.dart';
import '../application/manage_cache.dart';

/// One account-scoped owner; the selected workspace never retargets a transfer.
final class ModelAssetsController extends ChangeNotifier {
  ModelAssetsController(
    this.repository,
    this.files,
    this.checkpoints,
    this.cache,
    this.sessions,
  );
  final ModelAssetRepository repository;
  final LocalFilePort files;
  final UploadCheckpointPort checkpoints;
  final ModelCachePort cache;
  final SessionAccess sessions;
  String? userId;
  int userGeneration = -1;
  final _models = <String, List<ModelAsset>>{};
  final _listEpochs = <String, int>{};
  final _loading = <String>{};
  final _errors = <String, DomainError>{};
  final _cached = <String>{};
  final _deleting = <String>{};
  final _downloads = <String, Cancellation>{};
  final _downloadTasks = <String, Future<void>>{};
  final _downloadProjects = <String, String>{};
  final downloadProgress = <String, int>{};
  UploadCheckpoint? checkpoint;
  DomainError? error;
  Cancellation? _uploadCancellation;
  Future<void> _uploadTask = Future.value();
  Future<void> _restoreTask = Future.value();
  String? _uploadProjectId;
  bool uploadBusy = false, restoring = false, cacheBusy = false;
  int cacheBytes = 0, freedBytes = 0;
  List<ModelCacheEntry> cacheEntries = const [];
  bool _disposed = false;
  bool _foreground = true, _preparing = false;
  Completer<void>? _foregroundGate;
  Future<void> _awaitForeground(Cancellation cancellation) async {
    while (!_foreground && !cancellation.isCancelled) {
      final gate = _foregroundGate ??= Completer<void>();
      final cancelled = Completer<void>();
      final detach = cancellation.listen(() => cancelled.complete());
      await Future.any([gate.future, cancelled.future]);
      detach();
    }
  }

  void foreground() {
    _foreground = true;
    _foregroundGate?.complete();
    _foregroundGate = null;
    refreshLoaded();
  }

  void background() {
    _foreground = false;
    // SAF is another Activity: its pause must not cancel the user's selection.
    if (!_preparing) _uploadCancellation?.cancel();
    for (final token in _downloads.values) {
      token.cancel();
    }
  }

  List<ModelAsset> modelsFor(String projectId) =>
      _models[projectId] ?? const [];
  bool loading(String projectId) => _loading.contains(projectId);
  DomainError? listError(String projectId) => _errors[projectId];
  bool downloading(String id) => _downloads.containsKey(id);
  bool deleting(String id) => _deleting.contains(id);
  bool cached(ModelAsset model) =>
      userId != null && _cached.contains(ModelRevision.of(userId!, model).key);
  bool _current(int generation) =>
      !_disposed &&
      generation == userGeneration &&
      generation == sessions.generation &&
      userId == sessions.session?.userId;
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void synchronize() {
    if (userGeneration == sessions.generation &&
        userId == sessions.session?.userId) {
      return;
    }
    pause();
    userGeneration = sessions.generation;
    userId = sessions.session?.userId;
    checkpoint = null;
    error = null;
    uploadBusy = false;
    cacheBusy = false;
    restoring = false;
    _preparing = false;
    _uploadProjectId = null;
    cacheBytes = freedBytes = 0;
    cacheEntries = const [];
    _models.clear();
    _listEpochs.clear();
    _loading.clear();
    _errors.clear();
    _cached.clear();
    _deleting.clear();
    _downloads.clear();
    _downloadTasks.clear();
    _downloadProjects.clear();
    downloadProgress.clear();
    _notify();
    if (userId != null) _restoreTask = _restore(userGeneration, userId!);
  }

  Future<void> _restore(int generation, String account) async {
    restoring = true;
    _notify();
    final result = await checkpoints.read(account);
    if (!_current(generation)) return;
    if (result case Success<UploadCheckpoint?>(:final value)) {
      checkpoint = value;
      if (value?.phase == UploadPhase.completing ||
          value?.phase == UploadPhase.uncertain) {
        error = const DomainError('completion_uncertain');
      }
    } else if (result case Failure<UploadCheckpoint?>(:final error)) {
      this.error = error;
    }
    restoring = false;
    _notify();
  }

  Future<void> refresh(String projectId) async {
    final generation = userGeneration, account = userId;
    final epoch = _listEpochs[projectId] ?? 0;
    if (account == null ||
        !_current(generation) ||
        _loading.contains(projectId)) {
      return;
    }
    _loading.add(projectId);
    _errors.remove(projectId);
    _notify();
    final result = await repository.list(projectId, generation);
    if (!_current(generation)) return;
    if (epoch != (_listEpochs[projectId] ?? 0)) {
      _loading.remove(projectId);
      _notify();
      return;
    }
    if (result case Success<List<ModelAsset>>(:final value)) {
      _models[projectId] = List.unmodifiable(value);
      final retained = await cache.retain(account, projectId, value);
      if (!_current(generation)) return;
      if (retained case Failure<void>(:final error)) _errors[projectId] = error;
      for (final model in value) {
        final hit = await cache.acquire(account, model);
        if (_current(generation)) {
          _cached.remove(ModelRevision.of(account, model).key);
        }
        if (hit case Success<CacheLease?>(value: final lease?)) {
          lease.release();
          if (_current(generation)) {
            _cached.add(ModelRevision.of(account, model).key);
          }
        }
        if (!_current(generation)) return;
      }
    } else if (result case Failure<List<ModelAsset>>(:final error)) {
      _errors[projectId] = error;
    }
    _loading.remove(projectId);
    _notify();
  }

  Future<void> refreshLoaded() async {
    for (final id in List.of(_models.keys)) {
      await refresh(id);
    }
    final project = checkpoint?.projectId;
    if (project != null && !_models.containsKey(project)) {
      await refresh(project);
    }
  }

  Future<void> chooseAndUpload(String projectId) {
    if (uploadBusy || restoring || checkpoint != null || userId == null) {
      return Future.value();
    }
    final generation = userGeneration, account = userId!;
    final cancellation = Cancellation();
    _uploadCancellation = cancellation;
    _uploadProjectId = projectId;
    uploadBusy = true;
    _preparing = true;
    error = null;
    _notify();
    return _uploadTask = () async {
      final selected = await files.choose(cancellation);
      if (_current(generation)) _preparing = false;
      await _awaitForeground(cancellation);
      if (!_current(generation)) {
        if (selected case Success<LocalModelRef?>(value: final source?)) {
          await files.release(source);
        }
        return;
      }
      if (selected case Failure<LocalModelRef?>(:final error)) {
        this.error = error;
      }
      if (selected case Success<LocalModelRef?>(value: final source?)) {
        final result = await UploadModel(repository, files, checkpoints).create(
          account,
          projectId,
          source,
          generation,
          () => _current(generation),
          cancellation: cancellation,
          onCheckpoint: (state) {
            if (_current(generation)) {
              checkpoint = state;
              _notify();
            }
          },
        );
        if (result case Success<UploadCheckpoint>(:final value)) {
          if (_current(generation)) {
            checkpoint = value;
            await _run(generation, cancellation);
          }
        } else {
          if (checkpoint?.source.id != source.id) await files.release(source);
          if (result case Failure<UploadCheckpoint>(:final error)) {
            if (_current(generation)) this.error = error;
          }
        }
      }
      if (_current(generation)) {
        uploadBusy = false;
        _notify();
      }
    }();
  }

  Future<void> resume({bool retryCompletion = false}) {
    if (!_foreground ||
        uploadBusy ||
        restoring ||
        checkpoint == null ||
        userId == null) {
      return Future.value();
    }
    final generation = userGeneration, cancellation = Cancellation();
    _uploadCancellation = cancellation;
    uploadBusy = true;
    error = null;
    _notify();
    return _uploadTask = () async {
      await _run(generation, cancellation, retryCompletion: retryCompletion);
      if (_current(generation)) {
        uploadBusy = false;
        _notify();
      }
    }();
  }

  Future<void> _run(
    int generation,
    Cancellation cancellation, {
    bool retryCompletion = false,
  }) async {
    final current = checkpoint;
    if (current == null || !_current(generation)) return;
    final result = await UploadModel(repository, files, checkpoints).run(
      current,
      generation,
      cancellation,
      () => _current(generation),
      retryCompletion: retryCompletion,
      onCheckpoint: (state) {
        if (_current(generation)) {
          checkpoint = state;
          _notify();
        }
      },
    );
    if (!_current(generation)) return;
    if (result case Failure<ModelAsset>(error: final failure)) {
      error = failure;
    }
    if (result is Success<ModelAsset> ||
        checkpoint?.phase == UploadPhase.uncertain ||
        checkpoint?.phase == UploadPhase.completed) {
      await refresh(current.projectId);
    }
  }

  void pause() {
    _uploadCancellation?.cancel();
    for (final token in _downloads.values) {
      token.cancel();
    }
  }

  Future<void> reselectSource() async {
    if (uploadBusy || checkpoint == null) return;
    final generation = userGeneration,
        previous = checkpoint!,
        cancellation = Cancellation();
    _uploadCancellation = cancellation;
    uploadBusy = true;
    _preparing = true;
    _notify();
    final selected = await files.choose(cancellation);
    if (_current(generation)) _preparing = false;
    if (selected case Success<LocalModelRef?>(value: final source?)) {
      if (!_current(generation) || !previous.source.sameSource(source)) {
        await files.release(source);
        if (_current(generation)) error = const DomainError('source_changed');
      } else {
        final next = previous.copy(source: source);
        final saved = await checkpoints.write(next);
        if (saved is Success<void>) {
          if (previous.source.id != source.id) {
            await files.release(previous.source);
          }
          if (_current(generation)) {
            checkpoint = next;
            error = null;
          }
        } else {
          await files.release(source);
          if (_current(generation)) error = (saved as Failure<void>).error;
        }
      }
    } else if (selected case Failure<LocalModelRef?>(:final error)) {
      if (_current(generation)) this.error = error;
    }
    if (_current(generation)) {
      uploadBusy = false;
      _notify();
    }
  }

  Future<void> reconcile(String modelId) async {
    if (uploadBusy || checkpoint == null || checkpoint!.model != null) return;
    final generation = userGeneration, state = checkpoint!;
    uploadBusy = true;
    _notify();
    final owned = await repository.list(state.projectId, generation);
    if (!_current(generation)) return;
    if (owned case Success<List<ModelAsset>>(:final value)) {
      final model = value
          .where(
            (m) =>
                m.id == modelId &&
                m.projectId == state.projectId &&
                m.fileName == state.source.fileName &&
                m.sizeBytes == state.source.sizeBytes,
          )
          .firstOrNull;
      if (model == null) {
        error = const DomainError('model_unavailable');
      } else {
        final next = state.copy(phase: UploadPhase.completed, model: model);
        final saved = await checkpoints.write(next);
        if (_current(generation)) {
          checkpoint = next;
          error = saved is Failure<void>
              ? const DomainError('upload_completed_storage')
              : null;
        }
      }
    } else if (owned case Failure<List<ModelAsset>>(:final error)) {
      this.error = error;
    }
    if (_current(generation)) {
      uploadBusy = false;
      _notify();
    }
  }

  Future<void> dismissUpload() async {
    if (uploadBusy || checkpoint == null) return;
    final generation = userGeneration, state = checkpoint!;
    uploadBusy = true;
    _notify();
    final result = state.model == null
        ? await repository.abortUpload(state.sessionId, generation)
        : const Success<void>(null);
    if (!_current(generation)) return;
    if (result case Failure<void>(:final error)
        when error.code != 'not_found') {
      this.error = error;
    } else {
      final removed = await checkpoints.remove(state.userId);
      if (removed case Failure<void>(:final error)) {
        this.error = error;
      } else {
        final released = await files.release(state.source);
        if (_current(generation)) {
          checkpoint = null;
          error = released is Failure<void> ? released.error : null;
        }
      }
    }
    if (_current(generation)) {
      uploadBusy = false;
      _notify();
    }
  }

  Future<void> download(ModelAsset asset) {
    if (userId == null || _downloads.containsKey(asset.id)) {
      return Future.value();
    }
    final generation = userGeneration,
        account = userId!,
        cancel = Cancellation();
    _downloads[asset.id] = cancel;
    _downloadProjects[asset.id] = asset.projectId;
    error = null;
    _notify();
    return _downloadTasks[asset.id] = () async {
      try {
        final result = await DownloadModel(repository, cache)(
          account,
          asset,
          generation,
          cancel,
          () => _current(generation),
          onProgress: (received, total) {
            if (_current(generation)) {
              downloadProgress[asset.id] = received;
              _notify();
            }
          },
        );
        if (result case Success<CacheLease>(:final value)) {
          value.release();
          if (_current(generation)) {
            _cached.add(ModelRevision.of(account, asset).key);
          }
        } else if (result case Failure<CacheLease>(:final error)) {
          if (_current(generation)) this.error = error;
        }
      } finally {
        if (_current(generation)) {
          _downloads.remove(asset.id);
          _downloadProjects.remove(asset.id);
          _downloadTasks.remove(asset.id);
          downloadProgress.remove(asset.id);
          _notify();
        }
      }
    }();
  }

  void cancelDownload(String id) => _downloads[id]?.cancel();
  Future<void> delete(ModelAsset asset) async {
    if (userId == null || _deleting.contains(asset.id)) return;
    _deleting.add(asset.id);
    _notify();
    final generation = userGeneration, account = userId!;
    cancelDownload(asset.id);
    await _downloadTasks[asset.id];
    if (!_current(generation)) return;
    final result = await DeleteModel(repository, cache)(
      account,
      asset.id,
      generation,
      () => _current(generation),
    );
    if (!_current(generation)) return;
    if (result is Success<void> ||
        result is Failure<void> &&
            result.error.code == 'model_deleted_storage') {
      _listEpochs.update(
        asset.projectId,
        (value) => value + 1,
        ifAbsent: () => 1,
      );
      _models[asset.projectId] = modelsFor(asset.projectId)
          .where((m) => m.id != asset.id)
          .toList();
      _cached.remove(ModelRevision.of(account, asset).key);
    }
    error = result is Failure<void> ? result.error : null;
    _deleting.remove(asset.id);
    _notify();
  }

  Future<Result<void>> stopProjectTransfers(String projectId) async {
    final generation = userGeneration;
    await _restoreTask;
    if (!_current(generation)) return const Failure(DomainError('cancelled'));
    if (checkpoint?.projectId == projectId ||
        uploadBusy && _uploadProjectId == projectId) {
      _uploadCancellation?.cancel();
      await _uploadTask;
      if (!_current(generation)) return const Failure(DomainError('cancelled'));
      if (checkpoint?.projectId == projectId) {
        await dismissUpload();
        if (checkpoint != null) {
          return Failure(error ?? const DomainError('request'));
        }
      }
    }
    final ids = _downloadProjects.entries
        .where((e) => e.value == projectId)
        .map((e) => e.key)
        .toList();
    for (final id in ids) {
      cancelDownload(id);
    }
    for (final id in ids) {
      await _downloadTasks[id];
    }
    return _current(generation)
        ? const Success(null)
        : const Failure(DomainError('cancelled'));
  }

  Future<void> refreshCache() async {
    if (userId == null || cacheBusy) return;
    final generation = userGeneration, account = userId!;
    cacheBusy = true;
    _notify();
    final result = await ManageCache(cache).size(account);
    if (!_current(generation)) return;
    if (result case Success<int>(:final value)) {
      cacheBytes = value;
    } else if (result case Failure<int>(:final error)) {
      this.error = error;
    }
    final listed = await ManageCache(cache).entries(account);
    if (!_current(generation)) return;
    if (listed case Success<List<ModelCacheEntry>>(:final value)) {
      cacheEntries = value;
    } else if (listed case Failure<List<ModelCacheEntry>>(:final error)) {
      this.error = error;
    }
    cacheBusy = false;
    _notify();
  }

  Future<void> evictCache(ModelCacheEntry entry) async {
    if (userId == null || cacheBusy) return;
    final generation = userGeneration, account = userId!;
    cacheBusy = true;
    error = null;
    _notify();
    final result = await ManageCache(cache).evict(account, entry.key);
    if (!_current(generation)) return;
    if (result case Success<int>(:final value)) {
      freedBytes = value;
      _cached.remove(entry.key);
    } else if (result case Failure<int>(:final error)) {
      this.error = error;
    }
    cacheBusy = false;
    await refreshCache();
  }

  Future<void> clearCache() async {
    if (userId == null || cacheBusy) return;
    final generation = userGeneration, account = userId!;
    cacheBusy = true;
    error = null;
    _notify();
    final result = await ManageCache(cache).clear(account);
    if (!_current(generation)) return;
    if (result case Success<int>(:final value)) {
      freedBytes = value;
      _cached.clear();
    } else if (result case Failure<int>(:final error)) {
      this.error = error;
    }
    cacheBusy = false;
    await refreshCache();
  }

  @override
  void dispose() {
    pause();
    _disposed = true;
    super.dispose();
  }
}
