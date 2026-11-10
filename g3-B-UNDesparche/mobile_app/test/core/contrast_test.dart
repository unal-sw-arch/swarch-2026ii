import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/theme/app_colors.dart';

/// Contraste WCAG entre dos colores opacos.
double contrast(Color a, Color b) {
  double lum(Color c) {
    double ch(double v) => v <= 0.03928 ? v / 12.92 : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
    return 0.2126 * ch(c.r) + 0.7152 * ch(c.g) + 0.0722 * ch(c.b);
  }

  final la = lum(a), lb = lum(b);
  final hi = math.max(la, lb), lo = math.min(la, lb);
  return (hi + 0.05) / (lo + 0.05);
}

/// Pinta [fg] semitransparente sobre [bg].
Color over(Color fg, Color bg) => Color.alphaBlend(fg, bg);

void main() {
  // Texto pequeno (< 18 px) exige 4.5:1 (WCAG AA).
  const aa = 4.5;

  group('Contraste AA del texto pequeño - modo claro', () {
    final mintTintOnWhite = over(AppColors.mintTint, AppColors.lightSurface);
    final mintTintOnBg = over(AppColors.mintTint, AppColors.lightBackground);

    test('texto principal', () {
      expect(contrast(AppColors.lightTextPrimary, AppColors.lightBackground), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.lightTextPrimary, AppColors.lightSurface), greaterThanOrEqualTo(aa));
    });

    test('texto secundario pequeño (lightTextMuted)', () {
      expect(contrast(AppColors.lightTextMuted, AppColors.lightBackground), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.lightTextMuted, AppColors.lightSurface), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.lightTextMuted, mintTintOnBg), greaterThanOrEqualTo(aa));
    });

    test('menta como texto (mintInk) sobre fondo y tinte menta', () {
      expect(contrast(AppColors.mintInk, AppColors.lightBackground), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.mintInk, AppColors.lightSurface), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.mintInk, mintTintOnWhite), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.mintInk, mintTintOnBg), greaterThanOrEqualTo(aa));
    });

    test('texto del boton principal sobre menta', () {
      expect(contrast(const Color(0xFF032B1D), AppColors.mintPrimary), greaterThanOrEqualTo(aa));
    });
  });

  group('Contraste AA del texto pequeño - modo oscuro', () {
    final mintTintOnCard = over(AppColors.mintTint, AppColors.darkSurface);

    test('texto principal y secundario', () {
      expect(contrast(AppColors.darkTextPrimary, AppColors.darkBackground), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.darkTextSecondary, AppColors.darkBackground), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.darkTextSecondary, AppColors.darkSurface), greaterThanOrEqualTo(aa));
    });

    test('menta como texto (mintLime) sobre tarjeta y tinte', () {
      expect(contrast(AppColors.mintLime, AppColors.darkSurface), greaterThanOrEqualTo(aa));
      expect(contrast(AppColors.mintLime, mintTintOnCard), greaterThanOrEqualTo(aa));
    });
  });
}
