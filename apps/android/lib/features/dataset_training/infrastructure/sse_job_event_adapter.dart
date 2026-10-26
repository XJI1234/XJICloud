import 'dart:async';
import 'dart:convert';

import '../../../shared/domain/cancellation.dart';
import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../../../shared/infrastructure/api_client.dart';
import '../domain/job_event_port.dart';
import '../domain/training_job.dart';
import 'job_mapper.dart';

/// Incremental UTF8/CRLF parser. Neither a half frame nor a partial JSON object emits.
Stream<JobProgress> parseJobEvents(Stream<List<int>> bytes) async* {
  var buffer = '', event = '';
  final data = <String>[];
  var size = 0;
  await for (final chunk in bytes.transform(utf8.decoder)) {
    buffer += chunk;
    while (true) {
      final end = buffer.indexOf(RegExp(r'[\r\n]'));
      if (end < 0) {
        if (buffer.length > 65536) throw const FormatException('SSE size');
        break;
      }
      if (buffer[end] == '\r' && end == buffer.length - 1) break;
      final line = buffer.substring(0, end);
      final skip = buffer[end] == '\r' && buffer[end + 1] == '\n' ? 2 : 1;
      buffer = buffer.substring(end + skip);
      if (line.length > 65536) throw const FormatException('SSE line');
      if (line.isEmpty) {
        if (data.isNotEmpty && (event.isEmpty || event == 'progress')) {
          yield JobMapper.progress(jsonDecode(data.join('\n')));
        }
        data.clear();
        event = '';
        size = 0;
      } else if (!line.startsWith(':')) {
        final colon = line.indexOf(':');
        final field = colon < 0 ? line : line.substring(0, colon);
        var value = colon < 0 ? '' : line.substring(colon + 1);
        if (value.startsWith(' ')) value = value.substring(1);
        if (field == 'data') {
          size += value.length;
          if (size > 65536) throw const FormatException('SSE size');
          data.add(value);
        } else if (field == 'event') {
          event = value;
        }
      }
    }
  }
}

final class SseJobEventAdapter implements JobEventPort {
  SseJobEventAdapter(this.api);
  final ApiClient api;
  @override
  Stream<JobProgress> subscribe(
    String jobId,
    int generation,
    Cancellation cancellation,
  ) {
    final token = Cancellation();
    final detach = cancellation.listen(token.cancel);
    late final StreamController<JobProgress> controller;
    controller = StreamController<JobProgress>(
      onListen: () async {
        if (!JobMapper.validId(jobId)) {
          controller.addError(const DomainError('request'));
          await controller.close();
          detach();
          return;
        }
        final result = await api.request(
          'GET',
          '/api/v1/jobs/$jobId/events',
          authenticated: true,
          expectedGeneration: generation,
          cancellation: token,
          receiveTimeout: Duration.zero,
          consume: (bytes) async {
            await for (final progress in parseJobEvents(bytes)) {
              if (!token.isCancelled && progress.jobId == jobId) {
                controller.add(progress);
              }
            }
          },
        );
        if (result case Failure<Object?>(:final error)
            when !token.isCancelled) {
          controller.addError(error);
        }
        await controller.close();
        detach();
      },
      onCancel: token.cancel,
    );
    return controller.stream;
  }
}
