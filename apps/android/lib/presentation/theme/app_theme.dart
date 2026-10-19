import 'package:flutter/material.dart';

import 'app_tokens.dart';

abstract final class AppTheme {
  static ThemeData get light {
    const tokens = AppTokens();
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppTokens.controlRadius),
      borderSide: BorderSide(color: tokens.border),
    );
    return ThemeData(
      useMaterial3: true,
      extensions: const [tokens],
      scaffoldBackgroundColor: tokens.background,
      colorScheme: ColorScheme.light(
        primary: tokens.accent,
        onPrimary: Colors.white,
        primaryContainer: tokens.accentSoft,
        onPrimaryContainer: tokens.accent,
        surface: tokens.card,
        onSurface: tokens.text,
        onSurfaceVariant: tokens.textSecondary,
        error: tokens.error,
        outline: tokens.border,
        outlineVariant: tokens.divider,
        surfaceTint: Colors.transparent,
      ),
      textTheme: TextTheme(
        headlineLarge: TextStyle(
          fontSize: 28,
          color: tokens.text,
          fontWeight: FontWeight.w600,
        ),
        headlineMedium: TextStyle(
          fontSize: 24,
          color: tokens.text,
          fontWeight: FontWeight.w600,
        ),
        titleMedium: TextStyle(
          fontSize: 18,
          color: tokens.text,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(fontSize: 16, color: tokens.text),
        bodyMedium: TextStyle(fontSize: 16, color: tokens.text),
        bodySmall: TextStyle(fontSize: 14, color: tokens.textSecondary),
        labelLarge: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: tokens.card,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        constraints: const BoxConstraints(minHeight: 56),
        border: border,
        enabledBorder: border,
        focusedBorder: border.copyWith(
          borderSide: BorderSide(color: tokens.accent, width: 2),
        ),
        errorBorder: border.copyWith(
          borderSide: BorderSide(color: tokens.error),
        ),
        focusedErrorBorder: border.copyWith(
          borderSide: BorderSide(color: tokens.error, width: 2),
        ),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: tokens.card,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.cardRadius),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: tokens.card,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.cardRadius),
        ),
      ),
      dividerColor: tokens.divider,
      appBarTheme: AppBarTheme(
        backgroundColor: tokens.card,
        foregroundColor: tokens.text,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
      ),
    );
  }
}
