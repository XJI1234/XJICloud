import 'package:flutter/material.dart';

import '../../../shared/domain/result.dart';
import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../domain/privacy_document.dart';

/// Bundled policy: reading it requires neither network access nor SDK startup.
class PrivacyPolicyPage extends StatelessWidget {
  const PrivacyPolicyPage({super.key, required this.openDocument});
  final Future<Result<void>> Function(PrivacyDocument) openDocument;

  Future<void> _open(BuildContext context, PrivacyDocument document) async {
    final result = await openDocument(document);
    if (!context.mounted) return;
    if (result case Failure<void>(:final error)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(errorText(AppLocalizations.of(context), error))),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final sections = [
      (l.privacyCollectionTitle, l.privacyCollection),
      (l.privacyChoiceTitle, l.privacyChoice),
      (l.privacySdkTitle, l.privacySdk),
      (l.privacySharingTitle, l.privacySharing),
      (l.privacyStorageTitle, l.privacyStorage),
      (l.privacyRightsTitle, l.privacyRights),
      (l.privacyMinorsTitle, l.privacyMinors),
      (l.privacyUpdatesTitle, l.privacyUpdates),
    ];
    final documents = [
      (PrivacyDocument.numberAuthentication, l.privacyNumberPolicy),
      (PrivacyDocument.smsAuthentication, l.privacySmsPolicy),
      (PrivacyDocument.chinaMobile, l.privacyMobilePolicy),
      (PrivacyDocument.chinaUnicom, l.privacyUnicomPolicy),
      (PrivacyDocument.chinaTelecom, l.privacyTelecomPolicy),
    ];
    Widget paragraph(String text) => SelectableText(
      text,
      style: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.6),
    );
    return Scaffold(
      appBar: AppBar(title: Text(l.fusionPrivacyPolicy)),
      body: SafeArea(
        child: SingleChildScrollView(
          key: const Key('privacy-policy-content'),
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 720),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  paragraph(l.privacyEffectiveDate),
                  const SizedBox(height: 16),
                  paragraph(l.privacyIntroduction),
                  for (final section in sections) ...[
                    const SizedBox(height: 24),
                    Semantics(
                      header: true,
                      child: Text(
                        section.$1,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                    const SizedBox(height: 8),
                    paragraph(section.$2),
                    if (section.$1 == l.privacySdkTitle)
                      for (final document in documents)
                        Align(
                          alignment: Alignment.centerLeft,
                          child: TextButton(
                            key: ValueKey('privacy-source-${document.$1.name}'),
                            onPressed: () => _open(context, document.$1),
                            child: Text(
                              document.$2,
                              style: const TextStyle(
                                decoration: TextDecoration.underline,
                              ),
                            ),
                          ),
                        ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
