import 'package:flutter/material.dart';

import 'app/create_container.dart';
import 'app/product_app.dart';
export 'main_probe.dart' show XjiCloudProbeApp;

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    runApp(ProductApp(container: createContainer()));
  } on ArgumentError {
    runApp(const ConfigurationErrorApp());
  } on FormatException {
    runApp(const ConfigurationErrorApp());
  }
}
