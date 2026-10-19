import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

bool _registered = false;

/// Register the actual bundled SDK notices lazily, outside the UI boundary.
void registerSdkLicenses() {
  if (_registered) return;
  _registered = true;
  LicenseRegistry.addLicense(() async* {
    for (final entry in {
      'SPZ (Native3DGS SDK)': 'spz-LICENSE',
      'Zstandard (Native3DGS SDK)': 'zstd-LICENSE',
      'Android NDK toolchain (Native3DGS SDK)': 'android-ndk-NOTICE.toolchain',
    }.entries) {
      final notice = await rootBundle.loadString(
        'vendor/native3dgs/0.2.2/licenses/${entry.value}',
      );
      yield LicenseEntryWithLineBreaks([entry.key], notice);
    }
  });
}
