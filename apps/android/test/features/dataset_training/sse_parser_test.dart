import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/sse_job_event_adapter.dart';
import 'package:xjicloud_mobile/features/dataset_training/infrastructure/job_mapper.dart';
import 'package:xjicloud_mobile/features/dataset_training/domain/training_job.dart';

const id = '11111111-1111-1111-1111-111111111111';
void main() {
  test('half frames, UTF8 byte splits, CRLF, comments and multiline data', () async {
    final text =
        ':keep alive\r\nevent: progress\r\ndata: {"jobId":"$id",\r\ndata: "status":"RUNNING","progress":25,"message":"训练中"}\r\n\r\n';
    final bytes = utf8.encode(text);
    final result = await parseJobEvents(
      Stream.fromIterable([
        for (final b in bytes) [b],
      ]),
    ).toList();
    expect(result.length, 1);
    expect(result.single.message, '训练中');
    expect(result.single.status, JobStatus.running);
  });
  test(
    'incomplete frames do not emit and oversized frames are bounded',
    () async {
      expect(
        await parseJobEvents(Stream.value(utf8.encode('data: {"status":')))
            .toList(),
        isEmpty,
      );
      await expectLater(
        parseJobEvents(Stream.value(List.filled(65537, 97))).drain<void>(),
        throwsFormatException,
      );
    },
  );
  test(
    'manifest URL appears exactly once and inconsistent duplicate rejected',
    () {
      final payload = {
        'jobId': id,
        'manifestUploadUrl': 'https://oss.test/manifest',
        'uploads': [
          {
            'archivedName': '0001.jpg',
            'uploadUrl': 'https://oss.test/image',
            'contentType': 'image/jpeg',
          },
          {
            'archivedName': 'manifest.json',
            'uploadUrl': 'https://oss.test/manifest',
            'contentType': 'application/json',
          },
        ],
      };
      final ticket = JobMapper.ticket(payload);
      expect(ticket.uploads.length, 2);
      expect(ticket.uploads.keys.where((k) => k == 'manifest.json').length, 1);
      (payload['uploads'] as List).add({
        'archivedName': 'manifest.json',
        'uploadUrl': 'https://oss.test/other',
        'contentType': 'application/json',
      });
      expect(() => JobMapper.ticket(payload), throwsFormatException);
    },
  );
}
