import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../../presentation/widgets/app_button.dart';
import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../../../shared/domain/domain_error.dart';
import 'auth_controller.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({
    super.key,
    required this.controller,
    required this.onToggleLocale,
    this.sessionError,
    this.onRetrySession,
  });
  final AuthController controller;
  final VoidCallback onToggleLocale;
  final DomainError? sessionError;
  final VoidCallback? onRetrySession;
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final form = GlobalKey<FormState>();
  final confirmationFocus = FocusNode(), captchaFocus = FocusNode();
  final username = TextEditingController(),
      password = TextEditingController(),
      confirmation = TextEditingController(),
      displayName = TextEditingController(),
      code = TextEditingController();
  Timer? debounce;
  bool obscure = true;
  bool wasRegistered = false;
  String? challengeKey;
  AuthController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    c.addListener(_changed);
  }

  void _changed() {
    if (!mounted) return;
    if (c.captcha?.key != challengeKey) {
      challengeKey = c.captcha?.key;
      code.clear();
    }
    if (c.registered && !wasRegistered) {
      password.clear();
      confirmation.clear();
      code.clear();
    }
    wasRegistered = c.registered;
    setState(() {});
  }

  @override
  void dispose() {
    debounce?.cancel();
    confirmationFocus.dispose();
    captchaFocus.dispose();
    c.removeListener(_changed);
    for (final field in [username, password, confirmation, displayName, code]) {
      field.clear();
      field.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    debounce?.cancel();
    if (!(form.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();
    await c.submit(
      username: username.text,
      password: password.text,
      confirmation: confirmation.text,
      displayName: displayName.text,
      captchaCode: code.text,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final duration = MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : const Duration(milliseconds: 200);
    final error = c.error ?? widget.sessionError;
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            padding: EdgeInsets.fromLTRB(
              constraints.maxWidth < 600 ? 20 : 32,
              16,
              constraints.maxWidth < 600 ? 20 : 32,
              32,
            ),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        key: const Key('locale-toggle'),
                        onPressed: widget.onToggleLocale,
                        child: Text(l.toggleLanguage),
                      ),
                    ),
                    SizedBox(
                      height: MediaQuery.viewInsetsOf(context).bottom > 0
                          ? 8
                          : 32,
                    ),
                    Center(
                      child: Image.asset(
                        'assets/brand/logo_nw.png',
                        height: 96,
                        width: 96,
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      l.welcome,
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 24),
                    if (c.registered)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Semantics(
                          liveRegion: true,
                          child: Text(l.registrationSuccess),
                        ),
                      ),
                    if (error != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Semantics(
                          liveRegion: true,
                          child: Text(
                            errorText(l, error),
                            key: const Key('auth-error'),
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                      ),
                    if (widget.sessionError != null &&
                        widget.onRetrySession != null)
                      TextButton(
                        onPressed: widget.onRetrySession,
                        child: Text(l.retry),
                      ),
                    AutofillGroup(
                      child: Form(
                        key: form,
                        child: AnimatedSize(
                          duration: duration,
                          alignment: Alignment.topCenter,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              TextFormField(
                                key: const Key('username'),
                                controller: username,
                                enabled: !c.submitting,
                                autofillHints: const [AutofillHints.username],
                                textInputAction: TextInputAction.next,
                                autocorrect: false,
                                decoration: InputDecoration(
                                  labelText: l.username,
                                ),
                                validator: (value) =>
                                    value == null ||
                                        value.trim().length < 3 ||
                                        value.trim().length > 64
                                    ? l.usernameValidation
                                    : null,
                                onChanged: (value) {
                                  debounce?.cancel();
                                  debounce = Timer(
                                    const Duration(milliseconds: 350),
                                    () => c.checkUsername(value),
                                  );
                                },
                              ),
                              const SizedBox(height: 16),
                              TextFormField(
                                key: const Key('password'),
                                controller: password,
                                enabled: !c.submitting,
                                obscureText: obscure,
                                autocorrect: false,
                                enableSuggestions: false,
                                autofillHints: [
                                  c.registration
                                      ? AutofillHints.newPassword
                                      : AutofillHints.password,
                                ],
                                textInputAction:
                                    c.registration || c.captchaRequired
                                    ? TextInputAction.next
                                    : TextInputAction.done,
                                onFieldSubmitted: (_) {
                                  if (c.registration) {
                                    confirmationFocus.requestFocus();
                                  } else if (c.captchaRequired) {
                                    captchaFocus.requestFocus();
                                  } else {
                                    _submit();
                                  }
                                },
                                decoration: InputDecoration(
                                  labelText: l.password,
                                  suffixIcon: IconButton(
                                    tooltip: obscure
                                        ? l.showPassword
                                        : l.hidePassword,
                                    onPressed: () =>
                                        setState(() => obscure = !obscure),
                                    icon: Icon(
                                      obscure
                                          ? Icons.visibility_outlined
                                          : Icons.visibility_off_outlined,
                                    ),
                                  ),
                                ),
                                validator: (value) =>
                                    value == null ||
                                        value.length < 6 ||
                                        value.length > 128
                                    ? l.passwordValidation
                                    : null,
                              ),
                              if (c.registration) ...[
                                const SizedBox(height: 16),
                                TextFormField(
                                  key: const Key('confirm-password'),
                                  focusNode: confirmationFocus,
                                  controller: confirmation,
                                  enabled: !c.submitting,
                                  obscureText: obscure,
                                  autocorrect: false,
                                  enableSuggestions: false,
                                  textInputAction: TextInputAction.next,
                                  decoration: InputDecoration(
                                    labelText: l.confirmPassword,
                                  ),
                                  validator: (value) => value != password.text
                                      ? l.confirmationValidation
                                      : null,
                                ),
                                const SizedBox(height: 16),
                                TextFormField(
                                  key: const Key('display-name'),
                                  controller: displayName,
                                  enabled: !c.submitting,
                                  textInputAction: TextInputAction.next,
                                  decoration: InputDecoration(
                                    labelText: l.displayName,
                                  ),
                                  validator: (value) =>
                                      (value?.length ?? 0) > 128
                                      ? l.displayNameValidation
                                      : null,
                                ),
                              ],
                              if (c.captchaRequired) ...[
                                const SizedBox(height: 16),
                                TextFormField(
                                  key: const Key('captcha-code'),
                                  focusNode: captchaFocus,
                                  controller: code,
                                  enabled: !c.submitting,
                                  autocorrect: false,
                                  textInputAction: TextInputAction.done,
                                  onFieldSubmitted: (_) => _submit(),
                                  decoration: InputDecoration(
                                    labelText: l.captchaCode,
                                  ),
                                  validator: (value) =>
                                      value == null || value.trim().isEmpty
                                      ? l.requiredField
                                      : null,
                                ),
                                const SizedBox(height: 8),
                                Semantics(
                                  label: l.captchaCode,
                                  child: Align(
                                    alignment: Alignment.centerLeft,
                                    child: c.captcha == null
                                        ? Text(
                                            c.captchaLoading
                                                ? l.loading
                                                : l.errorCaptcha,
                                          )
                                        : Image.memory(
                                            Uint8List.fromList(
                                              c.captcha!.imageBytes,
                                            ),
                                            key: ValueKey(c.captcha!.key),
                                            height: 64,
                                            fit: BoxFit.contain,
                                            errorBuilder: (_, _, _) =>
                                                Text(l.errorProtocol),
                                          ),
                                  ),
                                ),
                                Align(
                                  alignment: Alignment.centerLeft,
                                  child: TextButton(
                                    key: const Key('captcha-refresh'),
                                    onPressed: c.submitting || c.captchaLoading
                                        ? null
                                        : c.refreshCaptcha,
                                    child: Text(l.refreshCaptcha),
                                  ),
                                ),
                              ],
                              const SizedBox(height: 24),
                              AppButton(
                                key: const Key('auth-submit'),
                                label: c.registration ? l.register : l.login,
                                onPressed: c.submitting ? null : _submit,
                                isLoading: c.submitting,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextButton(
                      key: const Key('auth-mode'),
                      onPressed: c.submitting
                          ? null
                          : () {
                              debounce?.cancel();
                              password.clear();
                              confirmation.clear();
                              code.clear();
                              form.currentState?.reset();
                              c.setRegistration(!c.registration);
                              if (!c.registration) {
                                c.checkUsername(username.text);
                              }
                            },
                      child: Text(
                        c.registration ? l.switchLogin : l.switchRegister,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
