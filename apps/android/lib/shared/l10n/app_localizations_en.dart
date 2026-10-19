// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'XJICloud';

  @override
  String get home => 'Home';

  @override
  String get projects => 'Projects';

  @override
  String get viewer => 'Model viewer';

  @override
  String get account => 'Account';

  @override
  String get upload => 'Upload';

  @override
  String get uploadImages => 'Upload images and train';

  @override
  String get uploadModel => 'Upload PLY/SPZ model';

  @override
  String get loading => 'Working';

  @override
  String get cancel => 'Cancel';

  @override
  String get confirm => 'Confirm';
}
