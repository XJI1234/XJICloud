import 'package:flutter/widgets.dart';

enum AdaptiveSize { phone, narrow, wide }

/// The owner keeps selection/controller state above this layout boundary.
/// Builders must retain stable keys for expensive children such as a Viewer.
class AdaptiveContent extends StatelessWidget {
  const AdaptiveContent({super.key, required this.builder});

  final Widget Function(BuildContext context, AdaptiveSize size) builder;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final width = constraints.maxWidth;
      final size = width < 600
          ? AdaptiveSize.phone
          : width < 840
          ? AdaptiveSize.narrow
          : AdaptiveSize.wide;
      return Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1280),
          child: builder(context, size),
        ),
      );
    },
  );
}
