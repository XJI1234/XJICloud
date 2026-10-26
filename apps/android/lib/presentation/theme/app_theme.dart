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
        secondary: tokens.accent,
        onSecondary: Colors.white,
        secondaryContainer: tokens.accentSoft,
        onSecondaryContainer: tokens.accent,
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
      cardTheme: CardThemeData(
        color: tokens.card,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: const EdgeInsets.only(bottom: 8),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.cardRadius),
        ),
        clipBehavior: Clip.antiAlias,
      ),
      listTileTheme: ListTileThemeData(
        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        iconColor: tokens.accent,
        selectedColor: tokens.accent,
        selectedTileColor: tokens.accentSoft,
        titleTextStyle: TextStyle(
          color: tokens.text,
          fontSize: 17,
          fontWeight: FontWeight.w600,
        ),
        subtitleTextStyle: TextStyle(
          color: tokens.textSecondary,
          fontSize: 14,
          height: 1.5,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      ),
      splashFactory: NoSplash.splashFactory,
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
        backgroundColor: tokens.background,
        foregroundColor: tokens.text,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: tokens.text,
          fontSize: 24,
          fontWeight: FontWeight.w600,
          letterSpacing: -.4,
        ),
      ),
    );
  }
}
