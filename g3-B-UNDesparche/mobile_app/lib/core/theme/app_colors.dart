import 'package:flutter/material.dart';

/// Tokens de color oficiales de UNDesparche segun STYLE_GUIDE_MOBILE.md
abstract class AppColors {
  // --- MINT PALETTE (HeroUI Mint Base) ---
  static const Color mintPrimary = Color(0xFF00D592);      // Mint 500: Botones principales y estados activos
  static const Color mintDark = Color(0xFF00BA7C);         // Mint 600: Hover/Press y enfasis
  static const Color mintFocus = Color(0xFF00875A);        // Mint Texto/Contraste AA en fondos claros
  static const Color mintLime = Color(0xFF00F5A0);         // Mint Acento alto contraste para Dark Mode
  static const Color mintInk = Color(0xFF00694A);          // Mint oscuro para TEXTO PEQUENO en modo claro (AA >= 4.5 sobre tinte menta)
  static const Color mintTint = Color(0x1F00D592);         // rgba(0, 213, 146, 0.12): Fondos de chips y badges
  static const Color mintBorder = Color(0x3B00D592);       // rgba(0, 213, 146, 0.23): Bordes sutiles con acento

  // --- SUPERFICIES Y FONDOS (Modo Claro) ---
  static const Color lightBackground = Color(0xFFF8FAFC);  // Gris muy claro neutro
  static const Color lightSurface = Color(0xFFFFFFFF);     // Fondo de tarjetas
  static const Color lightBorder = Color(0x14000000);      // Hairline 1px: rgba(0, 0, 0, 0.08)
  static const Color lightTextPrimary = Color(0xFF0A0F14); // Texto casi negro
  static const Color lightTextSecondary = Color(0xFF64748B);// Gris medio para subtitulos
  static const Color lightTextMuted = Color(0xFF4B5A6E);   // Gris para texto pequeno (<14 px): AA >= 4.5 sobre fondos claros y tintes

  // --- SUPERFICIES Y FONDOS (Modo Oscuro / Navy Profundo) ---
  static const Color darkBackground = Color(0xFF080E14);   // Navy profundo (#080E14)
  static const Color darkSurface = Color(0xFF0E1620);      // Superficie de tarjetas oscuras (#0E1620)
  static const Color darkBorder = Color(0x14FFFFFF);       // Hairline 1px: rgba(255, 255, 255, 0.08)
  static const Color darkTextPrimary = Color(0xFFF1F5F9);  // Texto blanco grisaceo
  static const Color darkTextSecondary = Color(0xFF94A3B8);// Gris tenue

  // --- ESTADOS SEMANTICOS ---
  static const Color success = Color(0xFF00D592);          // Disponible / Operacion exitosa
  static const Color warning = Color(0xFFF5A623);          // Reservado / Cuenta regresiva
  static const Color danger = Color(0xFFFF4D4F);           // Sancionado / No disponible / Cancelado
  static const Color occupied = Color(0xFF6366F1);         // Prestado / En uso (Indigo)
}
