import '../domain/training_job.dart';
import '../domain/training_job_repository.dart';

abstract final class JobMapper {
  static final _id = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  static bool validId(String id) => _id.hasMatch(id);
  static JobStatus status(Object? value) {
    if (value is! String) throw const FormatException('status');
    return JobStatus.values.firstWhere(
      (s) => s.name.toUpperCase() == value,
      orElse: () => throw const FormatException('status'),
    );
  }

  static String text(Object? value, {bool optional = false}) {
    if (value == null && optional) return '';
    if (value is! String || value.length > 16384) {
      throw const FormatException('text');
    }
    return value;
  }

  static TrainingJob job(Object? data) {
    if (data is! Map<String, dynamic> ||
        data['id'] is! String ||
        !validId(data['id']) ||
        data['projectId'] is! String ||
        !validId(data['projectId']) ||
        data['progress'] is! int ||
        data['progress'] < 0 ||
        data['progress'] > 100) {
      throw const FormatException('job');
    }
    final created = DateTime.tryParse(text(data['createdAt'])),
        updated = DateTime.tryParse(text(data['updatedAt']));
    if (created == null || updated == null) throw const FormatException('time');
    return TrainingJob(
      id: data['id'],
      projectId: data['projectId'],
      name: text(data['name']),
      status: status(data['status']),
      progress: data['progress'],
      stage: text(data['stage'], optional: true),
      message: text(data['message'], optional: true),
      downloadUrl: data['downloadUrl'] == null
          ? null
          : text(data['downloadUrl']),
      errorMessage: data['errorMessage'] == null
          ? null
          : text(data['errorMessage']),
      createdAt: created,
      updatedAt: updated,
    );
  }

  static JobProgress progress(Object? data) {
    if (data is! Map<String, dynamic> ||
        data['jobId'] is! String ||
        !validId(data['jobId']) ||
        data['progress'] is! int ||
        data['progress'] < 0 ||
        data['progress'] > 100) {
      throw const FormatException('progress');
    }
    return JobProgress(
      jobId: data['jobId'],
      status: status(data['status']),
      progress: data['progress'],
      stage: text(data['stage'], optional: true),
      message: text(data['message'], optional: true),
    );
  }

  static Uri signedUrl(String value) {
    final uri = Uri.tryParse(value);
    if (uri == null ||
        uri.scheme != 'https' ||
        !uri.hasAuthority ||
        uri.userInfo.isNotEmpty ||
        uri.hasFragment) {
      throw const FormatException('signed URL');
    }
    return uri;
  }

  static DatasetTicket ticket(Object? data) {
    if (data is! Map<String, dynamic> ||
        data['jobId'] is! String ||
        !validId(data['jobId']) ||
        data['uploads'] is! List) {
      throw const FormatException('ticket');
    }
    final uploads = <String, SignedUpload>{};
    var expires = DateTime.now().add(const Duration(minutes: 10));
    void add(String name, String url, String type) {
      final uri = signedUrl(url);
      if (name != 'manifest.json' &&
          !RegExp(r'^\d{4}\.(jpg|jpeg|png|webp)$').hasMatch(name)) {
        throw const FormatException('archive');
      }
      final old = uploads[name];
      if (old != null && (old.url != url || old.contentType != type)) {
        throw const FormatException('duplicate');
      }
      uploads[name] = SignedUpload(name, url, type);
      final epoch = int.tryParse(uri.queryParameters['Expires'] ?? '');
      if (epoch != null) {
        final time = DateTime.fromMillisecondsSinceEpoch(
          epoch * 1000,
          isUtc: true,
        );
        if (time.isBefore(expires)) expires = time;
      }
    }

    for (final item in data['uploads']) {
      if (item is! Map<String, dynamic>) throw const FormatException('upload');
      add(
        text(item['archivedName']),
        text(item['uploadUrl']),
        text(item['contentType']),
      );
    }
    final manifest = data['manifestUploadUrl'];
    if (manifest is String && manifest.isNotEmpty) {
      add('manifest.json', manifest, 'application/json');
    }
    if (!uploads.containsKey('manifest.json')) {
      throw const FormatException('manifest');
    }
    return DatasetTicket(data['jobId'], uploads, expires);
  }
}
