import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xjicloud_mobile/features/identity/domain/fusion_challenge.dart';
import 'package:xjicloud_mobile/features/identity/domain/privacy_document.dart';
import 'package:xjicloud_mobile/features/identity/infrastructure/platform_fusion_auth.dart';
import 'package:xjicloud_mobile/shared/domain/result.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.xjicloud.mobile/fusion_auth');
  const adapter = PlatformFusionAuth();
  const challenge = FusionChallenge(
    challengeId: 'server-id',
    authToken: 'sdk-secret',
    schemeCode: 'scheme',
    templateId: '100001',
    expiresInSeconds: 900,
  );
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('SIM capability and policy opening use the bridge without SDK initialization', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'openPrivacyDocument') {
        expect(call.arguments, {
          'document': PrivacyDocument.numberAuthentication.name,
        });
      }
      return call.method == 'canAuthenticate' ? false : null;
    });
    expect((await adapter.canAuthenticate() as Success<bool>).value, isFalse);
    expect(
      await adapter.openPrivacyDocument(PrivacyDocument.numberAuthentication),
      isA<Success<void>>(),
    );
    expect(calls, ['canAuthenticate', 'openPrivacyDocument']);
  });

  test(
    'missing bridge cannot report SIM support or silently open a policy',
    () async {
      expect((await adapter.canAuthenticate() as Success<bool>).value, isFalse);
      expect(
        (await adapter.openPrivacyDocument(
          PrivacyDocument.numberAuthentication,
        ) as Failure<void>).error.code,
        'policy_unavailable',
      );
    },
  );

  test('real method channel sends only server SDK arguments and accepts opaque string', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == 'authenticate' ? 'opaque+verify/token=' : null;
    });
    final result = await adapter.authenticate(challenge);
    expect((result as Success<String>).value, 'opaque+verify/token=');
    expect(calls.single.method, 'authenticate');
    expect(calls.single.arguments, {
      'authToken': 'sdk-secret',
      'schemeCode': 'scheme',
      'templateId': '100001',
      'privacyAccepted': true,
    });
    await adapter.complete(true);
    await adapter.complete(false);
    await adapter.cancel();
    expect(calls.map((c) => c.method), [
      'authenticate',
      'complete',
      'complete',
      'cancel',
    ]);
    expect(calls[1].arguments, {'success': true});
    expect(calls[2].arguments, {'success': false});
  });
  for (final code in [
    'cancelled',
    'fusion_unavailable',
    'unknown-provider-code',
  ]) {
    test(
      'native $code becomes safe stable domain error without provider message',
      () async {
        messenger.setMockMethodCallHandler(
          channel,
          (_) async => throw PlatformException(
            code: code,
            message: 'phone=13800138000 credential=secret',
            details: 'sdk-secret',
          ),
        );
        final result = await adapter.authenticate(challenge) as Failure<String>;
        expect(result.error.code, switch (code) {
          'cancelled' => 'cancelled',
          'fusion_unavailable' => 'fusion_unavailable',
          _ => 'fusion_failed',
        });
        expect(result.error.details, isNull);
        await adapter.complete(false);
        await adapter.cancel();
      },
    );
  }
  test('missing SDK and malformed blank token never authenticate', () async {
    expect(
      (await adapter.authenticate(challenge) as Failure<String>).error.code,
      'fusion_unavailable',
    );
    messenger.setMockMethodCallHandler(channel, (_) async => ' ');
    expect(
      (await adapter.authenticate(challenge) as Failure<String>).error.code,
      'fusion_failed',
    );
  });
}
