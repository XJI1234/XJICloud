import 'dart:async';

import 'package:flutter/material.dart';
import 'package:native3dgs_flutter/native3dgs_view.dart';

/// Temporary development entry. Product navigation belongs to later tasks.
class EngineProbePage extends StatefulWidget {
  const EngineProbePage({super.key});
  @override
  State<EngineProbePage> createState() => _EngineProbePageState();
}

class _EngineProbePageState extends State<EngineProbePage> {
  Native3dgsController? _controller;
  StreamSubscription<Native3dgsState>? _subscription;
  Native3dgsState _state = const Native3dgsState();
  String _capability = '检查设备能力…';
  String? _notice;
  bool _supported = false;
  bool _free = false;
  bool _flipY = false;
  bool _picking = false;
  Future<void> _created(Native3dgsController controller) async {
    _controller = controller;
    _subscription = controller.states.listen((state) {
      if (mounted) setState(() => _state = state);
    });
    final result = await controller.initialize();
    if (mounted) {
      setState(() {
        _supported = result.supported == true;
        _capability = result.message ?? '无法检查设备能力。';
      });
    }
  }

  Future<void> _open() async {
    setState(() {
      _picking = true;
      _notice = null;
    });
    final result = await _controller!.pickFile();
    if (!mounted) return;
    setState(() {
      _picking = false;
      _notice = result.message;
    });
    if (result.ok && result.uri != null) await _controller!.open(result.uri!);
  }

  Future<bool> _camera(String command, [List<double> values = const []]) async {
    final result = await _controller!.camera(command, values);
    if (mounted && !result.ok) setState(() => _notice = result.message);
    return result.ok;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Native3DGS · P0 开发验证')),
    body: SafeArea(
      child: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text('仅开发入口。未完成真机渲染验收；不代表产品查看器已交付。'),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Text(_capability, key: const Key('capability')),
          ),
          Expanded(
            child: ColoredBox(
              color: const Color(0xff10151c),
              child: Native3dgsView(onCreated: _created),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Text(
              '${_state.phase} · 场景点数 ${_state.sceneCount} · 请求 ${_state.requestId} / 活动 ${_state.activeRequestId}'
              '${_state.error == 0 ? '' : ' · SDK 错误 ${_state.error}'}'
              '${_state.message == null ? '' : '\n${_state.message}'}'
              '${_notice == null ? '' : '\n$_notice'}',
              textAlign: TextAlign.center,
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
            child: Wrap(
              spacing: 8,
              runSpacing: 4,
              alignment: WrapAlignment.center,
              children: [
                FilledButton(
                  onPressed: _supported && !_picking ? _open : null,
                  child: const Text('打开本机模型'),
                ),
                OutlinedButton(
                  onPressed: _supported ? () => _controller!.cancel() : null,
                  child: const Text('取消加载'),
                ),
                OutlinedButton(
                  onPressed: _supported ? () => _controller!.close() : null,
                  child: const Text('关闭模型'),
                ),
                TextButton(
                  onPressed: _supported ? () => _camera('fit') : null,
                  child: const Text('适配'),
                ),
                TextButton(
                  onPressed: _supported ? () => _camera('reset') : null,
                  child: const Text('重置'),
                ),
                TextButton(
                  onPressed: _supported
                      ? () async {
                          final ok = await _camera(_free ? 'fixed' : 'free');
                          if (mounted && ok) setState(() => _free = !_free);
                        }
                      : null,
                  child: Text(_free ? '固定模式' : '自由模式'),
                ),
                TextButton(
                  onPressed: _supported
                      ? () async {
                          final ok = await _camera('flipAxes', [
                            0,
                            _flipY ? 0 : 1,
                            0,
                          ]);
                          if (mounted && ok) setState(() => _flipY = !_flipY);
                        }
                      : null,
                  child: Text(_flipY ? '还原 Y 轴' : '翻转 Y 轴'),
                ),
                if (_free) ...[
                  IconButton(
                    tooltip: '向前移动',
                    onPressed: () => _camera('fly', [0, 0, 1, .15]),
                    icon: const Icon(Icons.arrow_upward),
                  ),
                  IconButton(
                    tooltip: '向后移动',
                    onPressed: () => _camera('fly', [0, 0, -1, .15]),
                    icon: const Icon(Icons.arrow_downward),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    ),
  );
  @override
  void dispose() {
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
