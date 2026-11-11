import 'package:flutter/material.dart';

/// Escala de espacio y formas, igual que en frontend_web.
abstract class AppSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;

  /// Margen lateral de las pantallas.
  static const double screenH = 16;

  /// Zona de toque minima (accesibilidad).
  static const double minTap = 48;
}

abstract class AppRadius {
  static const double card = 16;
  static const double sheet = 24;
  static const double pill = 9999;

  /// Barra de navegacion flotante y su indicador. El indicador es concentrico:
  /// 24 de la barra menos 6 de margen interior = 18.
  static const double navBar = 24;
  static const double navItem = 18;

  static BorderRadius get cardAll => BorderRadius.circular(card);
  static BorderRadius get pillAll => BorderRadius.circular(pill);
  static BorderRadius get navBarAll => BorderRadius.circular(navBar);
  static BorderRadius get navItemAll => BorderRadius.circular(navItem);
  static const BorderRadius sheetTop = BorderRadius.vertical(
    top: Radius.circular(sheet),
  );
}
