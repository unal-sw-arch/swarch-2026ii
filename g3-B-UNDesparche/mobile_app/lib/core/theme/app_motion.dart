import 'package:flutter/material.dart';

/// Tokens de movimiento, tomados de frontend_web/src/shared/motion/motionTokens.ts.
/// Entrada: opacidad 0->1 + subir 12 px. Sin rebote. Menos de 300 ms en acciones.
abstract class AppMotion {
  static const Duration fast = Duration(milliseconds: 120);
  static const Duration ui = Duration(milliseconds: 250);
  static const Duration enter = Duration(milliseconds: 400);
  static const Duration stagger = Duration(milliseconds: 50);

  /// Curva suave sin rebote.
  static const Curve curve = Cubic(0.16, 1, 0.3, 1);

  static const double enterOffset = 12;
  static const double pressScale = 0.97;

  /// Verdadero si el usuario pidio menos movimiento.
  static bool reduced(BuildContext context) =>
      MediaQuery.maybeOf(context)?.disableAnimations ?? false;
}
