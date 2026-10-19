import 'package:flutter/material.dart';

import 'app/engine_probe_page.dart';

void main() => runApp(const XjiCloudProbeApp());

class XjiCloudProbeApp extends StatelessWidget {
  const XjiCloudProbeApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'XJICloud Native3DGS P0',
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff3d6b8a)),
    ),
    home: const EngineProbePage(),
  );
}
