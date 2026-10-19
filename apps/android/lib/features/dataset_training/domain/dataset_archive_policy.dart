import '../../../shared/domain/result.dart';
import '../../../shared/domain/domain_error.dart';
import 'selected_image.dart';
import 'dataset_manifest.dart';

Result<DatasetManifest> archiveImages(List<SelectedImage> images) {
  if (images.isEmpty || images.length > 9999) {
    return const Failure(DomainError('dataset_validation'));
  }
  const types = {
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'png': 'image/png',
    'webp': 'image/webp',
  };
  final seen = <String>{};
  for (final image in images) {
    final ext = image.name.split('.').last.toLowerCase();
    final mime = image.contentType.toLowerCase();
    if (image.name.length > 255 ||
        !image.name.contains('.') ||
        image.name.contains(RegExp(r'[/\\\x00-\x1f]')) ||
        image.sizeBytes <= 0 ||
        image.relativeName.isEmpty ||
        image.relativeName.length > 4096 ||
        !seen.add(image.sourceId) ||
        image.sourceId.isEmpty ||
        !types.containsKey(ext) ||
        (mime.isNotEmpty &&
            mime != types[ext] &&
            !(ext == 'jpg' && mime == 'image/jpg'))) {
      return const Failure(DomainError('dataset_validation'));
    }
  }
  final sorted = List.of(images)
    ..sort((a, b) {
      final result = a.relativeName.compareTo(b.relativeName);
      if (result != 0) return result;
      final fingerprint = a.fingerprint.compareTo(b.fingerprint);
      return fingerprint == 0 ? a.sourceId.compareTo(b.sourceId) : fingerprint;
    });
  return Success(
    DatasetManifest([
      for (var i = 0; i < sorted.length; i++)
        ArchivedImage(
          sorted[i],
          '${(i + 1).toString().padLeft(4, '0')}.${sorted[i].name.split('.').last.toLowerCase()}',
          types[sorted[i].name.split('.').last.toLowerCase()]!,
        ),
    ]),
  );
}
