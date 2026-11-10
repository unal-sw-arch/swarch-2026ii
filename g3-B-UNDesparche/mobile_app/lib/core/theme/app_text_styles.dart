import 'package:flutter/material.dart';
import 'app_colors.dart';

/// Estilos de texto derivados del tema (#12). Sustituye a AppTypography:
/// el color y la familia salen del `textTheme`, no de un `isDark` manual.
/// Solo quedan aquí los casos especializados que el textTheme no cubre tal cual
/// (temporizador con cifras tabulares, texto de botón sólido sobre menta).
extension AppText on BuildContext {
  TextTheme get _t => Theme.of(this).textTheme;

  // Titulares (Varela Round vía textTheme).
  TextStyle get displayLarge =>
      _t.headlineSmall!.copyWith(fontSize: 28, fontWeight: FontWeight.bold, letterSpacing: -0.5);
  TextStyle get displayMedium =>
      _t.titleLarge!.copyWith(fontSize: 22, fontWeight: FontWeight.w700);
  TextStyle get displaySmall =>
      _t.titleLarge!.copyWith(fontSize: 18, fontWeight: FontWeight.w600);

  // Cuerpo (Plus Jakarta Sans vía textTheme).
  TextStyle get bodyLarge => _t.bodyLarge!.copyWith(fontSize: 16, height: 1.5);
  TextStyle get bodyMedium =>
      _t.bodyMedium!.copyWith(fontSize: 14, height: 1.4, color: _muted);
  TextStyle get bodySmall => _t.bodySmall!.copyWith(fontSize: 12, color: _muted);

  Color get _muted => Theme.of(this).brightness == Brightness.dark
      ? AppColors.darkTextSecondary
      : AppColors.lightTextMuted;
}

/// Estilos independientes de contexto (color fijo por diseño).
abstract class AppTextStyles {
  /// Texto de botón sólido: contraste verde oscuro sobre menta.
  static TextStyle buttonSolid(TextStyle? base) => (base ?? const TextStyle()).copyWith(
        fontSize: 15,
        fontWeight: FontWeight.w700,
        color: const Color(0xFF032B1D),
        letterSpacing: 0.2,
      );

  /// Cifras del temporizador de 10 min (RN-02): cifras tabulares.
  static TextStyle timerDigits(TextStyle? base) => (base ?? const TextStyle()).copyWith(
        fontSize: 28,
        fontWeight: FontWeight.w800,
        color: AppColors.warning,
        fontFeatures: const [FontFeature.tabularFigures()],
      );
}
