// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'XJICloud';

  @override
  String get home => '主页';

  @override
  String get projects => '工程管理';

  @override
  String get viewer => '模型查看';

  @override
  String get account => '我的';

  @override
  String get upload => '上传';

  @override
  String get uploadImages => '上传图片并训练';

  @override
  String get uploadModel => '上传 PLY/SPZ 模型';

  @override
  String get loading => '处理中';

  @override
  String get cancel => '取消';

  @override
  String get confirm => '确认';
}
