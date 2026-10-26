import 'package:flutter/material.dart';

@immutable
class AppTokens extends ThemeExtension<AppTokens> {
  const AppTokens();

  final Color background = const Color(0xfff5f6f8);
  final Color card = const Color(0xffffffff);
  final Color loginBackground = const Color(0xffffffff);
  final Color text = const Color(0xff1c1f24);
  final Color textSecondary = const Color(0xff5c6370);
  final Color textWeak = const Color(0xff8b919c);
  final Color accent = const Color(0xff3d6b8a);
  final Color accentPressed = const Color(0xff345c76);
  final Color error = const Color(0xffb42318);
  final Color success = const Color(0xff2f6f4e);
  Color get accentSoft => accent.withValues(alpha: .12);
  Color get divider => text.withValues(alpha: .08);
  Color get border => text.withValues(alpha: .14);

  static const double controlRadius = 10;
  static const double cardRadius = 16;
  static const double pillRadius = 999;
  static const double spaceUnit = 4;
  static const double space8 = 8;
  static const double space12 = 12;
  static const double space16 = 16;
  static const double space24 = 24;
  static const double space32 = 32;
  static const double phoneMargin = 20;
  static const double padMargin = 32;
  static const double minimumTarget = 48;
  static const Duration pressDuration = Duration(milliseconds: 100);
  static const Duration sheetDuration = Duration(milliseconds: 200);
  static const Duration pageDuration = Duration(milliseconds: 220);

  @override
  AppTokens copyWith() => this;

  @override
  AppTokens lerp(covariant AppTokens? other, double t) => this;
}
