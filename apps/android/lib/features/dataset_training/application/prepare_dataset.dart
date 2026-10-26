import '../domain/dataset_archive_policy.dart';
import '../domain/dataset_manifest.dart';
import '../domain/selected_image.dart';
import '../../../shared/domain/result.dart';

Result<DatasetManifest> prepareDataset(List<SelectedImage> images) =>
    archiveImages(images);
