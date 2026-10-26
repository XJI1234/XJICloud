import 'package:flutter/material.dart';

import '../../../presentation/widgets/app_button.dart';
import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import 'profile_controller.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({
    super.key,
    required this.controller,
    required this.requiredProfile,
    required this.onLogout,
    required this.onSaved,
  });
  final ProfileController controller;
  final bool requiredProfile;
  final VoidCallback onLogout, onSaved;
  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final name = TextEditingController(),
      email = TextEditingController(),
      organization = TextEditingController();
  bool filled = false;
  ProfileController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    c.addListener(changed);
    c.load();
  }

  void changed() {
    if (!mounted) return;
    final profile = c.profile;
    if (!filled && profile != null) {
      name.text = profile.displayName;
      email.text = profile.email;
      organization.text = profile.organization;
      filled = true;
    }
    setState(() {});
  }

  @override
  void dispose() {
    c.removeListener(changed);
    name.dispose();
    email.dispose();
    organization.dispose();
    super.dispose();
  }

  Future<void> save() async {
    FocusScope.of(context).unfocus();
    await c.save(
      displayName: name.text,
      email: email.text,
      organization: organization.text,
    );
    if (mounted && c.saved) widget.onSaved();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final busy = c.loading || c.saving;
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.requiredProfile ? l.completeProfileTitle : l.editProfile,
        ),
        automaticallyImplyLeading: !widget.requiredProfile,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (widget.requiredProfile) ...[
                    Text(l.profileIntro),
                    const SizedBox(height: 24),
                  ],
                  if (c.loading) const LinearProgressIndicator(),
                  if (c.error != null) ...[
                    Text(
                      errorText(l, c.error!),
                      key: const Key('profile-error'),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                    if (c.profile == null)
                      TextButton(
                        onPressed: busy ? null : c.load,
                        child: Text(l.retry),
                      ),
                    const SizedBox(height: 16),
                  ],
                  TextField(
                    key: const Key('profile-display-name'),
                    controller: name,
                    enabled: !busy,
                    maxLength: 128,
                    textInputAction: TextInputAction.next,
                    decoration: InputDecoration(labelText: l.displayName),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    key: const Key('profile-email'),
                    controller: email,
                    enabled: !busy,
                    maxLength: 254,
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                    autofillHints: const [AutofillHints.email],
                    decoration: InputDecoration(labelText: l.profileEmail),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    key: const Key('profile-organization'),
                    controller: organization,
                    enabled: !busy,
                    maxLength: 200,
                    textInputAction: TextInputAction.done,
                    onSubmitted: busy ? null : (_) => save(),
                    decoration: InputDecoration(
                      labelText: l.profileOrganization,
                    ),
                  ),
                  const SizedBox(height: 24),
                  AppButton(
                    key: const Key('profile-save'),
                    label: l.saveProfile,
                    onPressed: busy ? null : save,
                  ),
                  if (widget.requiredProfile) ...[
                    const SizedBox(height: 16),
                    TextButton(
                      key: const Key('profile-logout'),
                      onPressed: widget.onLogout,
                      child: Text(l.logout),
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
