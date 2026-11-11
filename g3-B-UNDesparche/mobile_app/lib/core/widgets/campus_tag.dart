import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

/// Etiqueta de edificio del campus, como en EventCard.tsx. Ej: "ED. 453 · CYT".
class CampusTag extends StatelessWidget {
  final String label;

  const CampusTag({super.key, required this.label});

  /// Crea la etiqueta a partir del nombre del lugar. Devuelve null si el lugar esta vacio.
  static CampusTag? fromPlace(String? place) {
    final text = campusLabel(place);
    return text == null ? null : CampusTag(label: text);
  }

  /// Misma regla que `getCampusTag` en frontend_web.
  static String? campusLabel(String? place) {
    if (place == null || place.trim().isEmpty) return null;
    final p = place.toLowerCase();
    if (p.contains('453') || p.contains('cyt') || p.contains('tecnolog')) {
      return 'ED. 453 · CYT';
    }
    if (p.contains('411') || p.contains('minas')) return 'ED. 411 · MINAS';
    if (p.contains('401') || p.contains('ingenier')) return 'ED. 401 · ING';
    if (p.contains('102') ||
        p.contains('ché') ||
        p.contains('che') ||
        p.contains('biblioteca')) {
      return 'ED. 102 · PLAZA CHÉ';
    }
    return 'CAMPUS UNAL';
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final fg = isDark ? AppColors.mintLime : AppColors.mintInk;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.mintTint,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.mintBorder),
      ),
      child: Text(
        label,
        maxLines: 1,
        softWrap: false,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 10.5,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.6,
          color: fg,
        ),
      ),
    );
  }
}
