import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../../presentation/widgets/app_button.dart';
import '../../../shared/l10n/app_localizations.dart';
import '../../../shared/presentation/error_text.dart';
import '../../../shared/domain/domain_error.dart';
import '../domain/captcha_challenge.dart';
import 'auth_controller.dart';
import 'privacy_policy_page.dart';

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
      phone = TextEditingController(),
      password = TextEditingController(),
      confirmation = TextEditingController(),
      code = TextEditingController(),
      sms = TextEditingController();
  Timer? debounce;
  bool obscure = true;
  String? challengeKey;
  CaptchaChallenge? imageChallenge;
  MemoryImage? captchaImage;
  int smsRevision = 0;
  AuthMode? previousMode;
  AuthController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    previousMode = c.mode;
    _cacheCaptcha();
    c.addListener(_changed);
  }

  void _cacheCaptcha() {
    if (identical(imageChallenge, c.captcha)) return;
    imageChallenge = c.captcha;
    captchaImage = imageChallenge == null
        ? null
        : MemoryImage(Uint8List.fromList(imageChallenge!.imageBytes));
  }

  void _changed() {
    if (!mounted) return;
    _cacheCaptcha();
    if (c.captcha?.key != challengeKey) {
      challengeKey = c.captcha?.key;
      code.clear();
    }
    if (c.smsRevision != smsRevision) {
      smsRevision = c.smsRevision;
      sms.clear();
    }
    if (previousMode != c.mode) {
      previousMode = c.mode;
      password.clear();
      confirmation.clear();
      code.clear();
      sms.clear();
    }
    setState(() {});
  }

  @override
  void dispose() {
    debounce?.cancel();
    confirmationFocus.dispose();
    captchaFocus.dispose();
    c.removeListener(_changed);
    for (final field in [username, phone, password, confirmation, code, sms]) {
      field.clear();
      field.dispose();
    }
    super.dispose();
  }

  void _switch(AuthMode mode) {
    debounce?.cancel();
    form.currentState?.reset();
    switch (mode) {
      case AuthMode.fusion:
        c.setFusionLogin();
      case AuthMode.password:
        c.setPasswordLogin();
      case AuthMode.registration:
        c.setRegistration(true);
    }
  }

  void _openPrivacyPolicy() => Navigator.of(context).push<void>(
    MaterialPageRoute(
      builder: (_) => PrivacyPolicyPage(openDocument: c.openPrivacyDocument),
    ),
  );

  Future<void> _submit() async {
    debounce?.cancel();
    if (!(form.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();
    await c.submit(
      username: username.text,
      phone: phone.text,
      password: password.text,
      confirmation: confirmation.text,
      captchaCode: code.text,
      smsCode: sms.text,
    );
  }

  String _errorText(AppLocalizations l, DomainError e) => switch (e.code) {
    'privacy_required' => l.privacyRequired,
    'fusion_unavailable' => l.fusionUnavailable,
    'fusion_failed' => l.fusionFailed,
    'sms_required' => l.smsRequired,
    'registration_validation' => l.registrationValidation,
    _ => errorText(l, e),
  };

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
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
                        height: 80,
                        width: 80,
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      l.welcome,
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 24),
                    if (error != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Semantics(
                          liveRegion: true,
                          child: Text(
                            _errorText(l, error),
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
                    if (c.mode == AuthMode.fusion) ...[
                      Text(l.fusionPrivacyPurpose),
                      const SizedBox(height: 8),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Checkbox(
                            key: const Key('privacy-consent'),
                            value: c.privacyAccepted,
                            semanticLabel:
                                '${l.privacyConsent}${l.fusionPrivacyPolicy}',
                            onChanged: (value) =>
                                c.setPrivacyAccepted(value ?? false),
                          ),
                          Expanded(
                            child: Wrap(
                              crossAxisAlignment: WrapCrossAlignment.center,
                              children: [
                                Text(l.privacyConsent),
                                TextButton(
                                  key: const Key('privacy-policy'),
                                  onPressed: _openPrivacyPolicy,
                                  child: Text(
                                    l.fusionPrivacyPolicy,
                                    style: const TextStyle(
                                      decoration: TextDecoration.underline,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      AppButton(
                        key: const Key('fusion-submit'),
                        label: l.fusionLogin,
                        isLoading: c.submitting,
                        onPressed: c.submitting ? null : c.startFusion,
                      ),
                    ] else
                      AutofillGroup(
                        child: Form(
                          key: form,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              if (c.registration)
                                TextFormField(
                                  key: const Key('phone'),
                                  controller: phone,
                                  keyboardType: TextInputType.phone,
                                  autofillHints: const [
                                    AutofillHints.telephoneNumberNational,
                                  ],
                                  textInputAction: TextInputAction.next,
                                  decoration: InputDecoration(
                                    labelText: l.phoneNumber,
                                  ),
                                  onChanged: c.changePhone,
                                  validator: (value) =>
                                      value == null ||
                                          !RegExp(r'^1[3-9][0-9]{9}$')
                                              .hasMatch(value.trim())
                                      ? l.phoneValidation
                                      : null,
                                )
                              else
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
                              if (c.captchaRequired) ..._captcha(l),
                              if (c.registration) ...[
                                const SizedBox(height: 12),
                                OutlinedButton(
                                  key: const Key('sms-send'),
                                  onPressed:
                                      c.submitting || c.retryAfterSeconds > 0
                                      ? null
                                      : () async {
                                          if (c.smsChallenge != null &&
                                              !c.captchaRequired) {
                                            await c.requestSmsResend();
                                            return;
                                          }
                                          if (!RegExp(
                                                r'^1[3-9][0-9]{9}$',
                                              ).hasMatch(phone.text.trim()) ||
                                              code.text.trim().isEmpty) {
                                            form.currentState?.validate();
                                            return;
                                          }
                                          await c.sendSms(
                                            phone: phone.text,
                                            captchaCode: code.text,
                                          );
                                        },
                                  child: Text(
                                    c.retryAfterSeconds > 0
                                        ? '${l.resendSms} (${c.retryAfterSeconds}s)'
                                        : c.smsChallenge != null
                                        ? l.resendSms
                                        : l.sendSms,
                                  ),
                                ),
                              ],
                              if (!c.registration ||
                                  c.smsChallenge != null) ...[
                                if (c.registration) ...[
                                  const SizedBox(height: 16),
                                  TextFormField(
                                    key: const Key('sms-code'),
                                    controller: sms,
                                    enabled: !c.submitting,
                                    keyboardType: TextInputType.number,
                                    autofillHints: const [
                                      AutofillHints.oneTimeCode,
                                    ],
                                    textInputAction: TextInputAction.next,
                                    decoration: InputDecoration(
                                      labelText: l.smsCode,
                                    ),
                                    validator: (value) =>
                                        value == null ||
                                            !RegExp(r'^[0-9]{6}$')
                                                .hasMatch(value)
                                        ? l.smsValidation
                                        : null,
                                  ),
                                ],
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
                                  validator: (value) {
                                    if (c.registration) {
                                      return value == null ||
                                              value.runes.length < 8 ||
                                              value.runes.length > 64 ||
                                              utf8.encode(value).length > 72
                                          ? l.registrationPasswordValidation
                                          : null;
                                    }
                                    return value == null ||
                                            value.length < 6 ||
                                            value.length > 128
                                        ? l.passwordValidation
                                        : null;
                                  },
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
                                    textInputAction: TextInputAction.done,
                                    onFieldSubmitted: (_) => _submit(),
                                    decoration: InputDecoration(
                                      labelText: l.confirmPassword,
                                    ),
                                    validator: (value) => value != password.text
                                        ? l.confirmationValidation
                                        : null,
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
                            ],
                          ),
                        ),
                      ),
                    const SizedBox(height: 16),
                    Wrap(
                      alignment: WrapAlignment.center,
                      spacing: 8,
                      children: [
                        if (c.mode != AuthMode.password)
                          TextButton(
                            key: const Key('password-mode'),
                            onPressed: () => _switch(AuthMode.password),
                            child: Text(l.passwordLogin),
                          ),
                        if (c.mode != AuthMode.registration)
                          TextButton(
                            key: const Key('auth-mode'),
                            onPressed: () => _switch(AuthMode.registration),
                            child: Text(l.phoneRegistration),
                          ),
                        if (c.mode != AuthMode.fusion)
                          TextButton(
                            key: const Key('fusion-mode'),
                            onPressed: () => _switch(AuthMode.fusion),
                            child: Text(l.fusionLogin),
                          ),
                      ],
                    ),
                    if (c.mode != AuthMode.fusion)
                      TextButton(
                        key: const Key('privacy-policy'),
                        onPressed: _openPrivacyPolicy,
                        child: Text(l.fusionPrivacyPolicy),
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

  List<Widget> _captcha(AppLocalizations l) => [
    const SizedBox(height: 16),
    Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: TextFormField(
            key: const Key('captcha-code'),
            focusNode: captchaFocus,
            controller: code,
            enabled: !c.submitting,
            autocorrect: false,
            textInputAction: TextInputAction.next,
            decoration: InputDecoration(
              labelText: l.captchaCode,
              errorMaxLines: 3,
            ),
            validator: (value) =>
                value == null || value.trim().isEmpty ? l.requiredField : null,
          ),
        ),
        const SizedBox(width: 12),
        SizedBox(
          width: 120,
          height: 56,
          child: Semantics(
            label: l.captchaCode,
            child: captchaImage == null
                ? Center(
                    child: Text(c.captchaLoading ? l.loading : l.errorCaptcha),
                  )
                : Image(
                    image: captchaImage!,
                    key: ValueKey(c.captcha!.key),
                    fit: BoxFit.contain,
                    errorBuilder: (_, _, _) => Text(l.errorProtocol),
                  ),
          ),
        ),
      ],
    ),
    Align(
      alignment: Alignment.centerLeft,
      child: TextButton(
        key: const Key('captcha-refresh'),
        onPressed: c.submitting || c.captchaLoading ? null : c.refreshCaptcha,
        child: Text(l.refreshCaptcha),
      ),
    ),
  ];
}
