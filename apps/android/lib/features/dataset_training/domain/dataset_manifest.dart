import 'dart:convert';

import 'selected_image.dart';

final class ArchivedImage {
  const ArchivedImage(this.source, this.archivedName, this.contentType);
  final SelectedImage source;
  final String archivedName, contentType;
}

/// The archive format is a domain document, distinct from API transport DTOs.
final class DatasetManifest {
  DatasetManifest(List<ArchivedImage> images)
    : files = List.unmodifiable(images);
  final List<ArchivedImage> files;
  late final List<int> bytes = List.unmodifiable(
    utf8.encode(
      jsonEncode({
        'version': 1,
        'imageCount': files.length,
        'files': [
          for (final f in files)
            {
              'archivedName': f.archivedName,
              'originalName': f.source.name,
              'contentType': f.contentType,
              'sizeBytes': f.source.sizeBytes,
            },
        ],
      }),
    ),
  );
  late final int totalBytes = files.fold(
    bytes.length,
    (n, f) => n + f.source.sizeBytes,
  );
}
