import 'package:flutter/services.dart';

import '../../../shared/domain/domain_error.dart';
import '../../../shared/domain/result.dart';
import '../domain/fusion_auth_port.dart';
import '../domain/fusion_challenge.dart';
import '../domain/privacy_document.dart';

final class PlatformFusionAuth implements FusionAuthPort {
  const PlatformFusionAuth();
  static const _channel = MethodChannel('com.xjicloud.mobile/fusion_auth');

  @override
  Future<Result<bool>> canAuthenticate() async {
    try {
      return Success(
        await _channel.invokeMethod<bool>('canAuthenticate') ?? false,
      );
    } on MissingPluginException {
      return const Success(false);
    } on PlatformException {
      return const Failure(DomainError('fusion_unavailable'));
    }
  }

  @override
  Future<Result<void>> openPrivacyDocument(PrivacyDocument document) async {
    try {
      await _channel.invokeMethod<void>('openPrivacyDocument', {
        'document': document.name,
      });
      return const Success(null);
    } on MissingPluginException {
      return const Failure(DomainError('policy_unavailable'));
    } on PlatformException {
      return const Failure(DomainError('policy_unavailable'));
    }
  }

  @override
  Future<Result<String>> authenticate(FusionChallenge challenge) async {
    try {
      final token = await _channel.invokeMethod<String>('authenticate', {
        'authToken': challenge.authToken,
        'schemeCode': challenge.schemeCode,
        'templateId': challenge.templateId,
        'privacyAccepted': true,
      });
      if (token == null || token.trim().isEmpty) {
        return const Failure(DomainError('fusion_failed'));
      }
      return Success(token);
    } on MissingPluginException {
      return const Failure(DomainError('fusion_unavailable'));
    } on PlatformException catch (error) {
      return Failure(
        DomainError(switch (error.code.toLowerCase()) {
          'cancelled' || 'canceled' || 'user_cancelled' => 'cancelled',
          'fusion_unavailable' ||
          'unavailable' ||
          'sdk_unavailable' ||
          'unsupported' ||
          'not_configured' => 'fusion_unavailable',
          _ => 'fusion_failed',
        }),
      );
    } on Object {
      return const Failure(DomainError('fusion_failed'));
    }
  }

  @override
  Future<void> complete(bool success) =>
      _release('complete', {'success': success});
  @override
  Future<void> cancel() => _release('cancel', null);

  Future<void> _release(String method, Map<String, Object>? arguments) async {
    try {
      await _channel.invokeMethod<void>(method, arguments);
    } on MissingPluginException {
      // No native session exists on an unsupported platform.
      return;
    } on PlatformException {
      // Native completion and cancellation are idempotent resource release.
      return;
    }
  }
}
