import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../../features/inventory/domain/entities/implement_entity.dart';

/// Pildora de estado segun el sistema HeroUI Mint con tinte translucido del 12%.
class HeroStatusChip extends StatelessWidget {
  final String label;
  final Color baseColor;

  const HeroStatusChip({
    super.key,
    required this.label,
    required this.baseColor,
  });

  /// Constructor de conveniencia para estados de implementos
  factory HeroStatusChip.fromImplementStatus(ImplementStatus status) {
    switch (status) {
      case ImplementStatus.available:
        return const HeroStatusChip(label: 'Disponible', baseColor: AppColors.success);
      case ImplementStatus.reserved:
        return const HeroStatusChip(label: 'Reservado (10m)', baseColor: AppColors.warning);
      case ImplementStatus.borrowed:
        return const HeroStatusChip(label: 'En Prestamo', baseColor: AppColors.occupied);
      case ImplementStatus.unavailable:
        return const HeroStatusChip(label: 'No Disponible', baseColor: AppColors.danger);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    // El texto se ajusta para llegar a contraste AA; el tinte y el borde usan el color base.
    final hsl = HSLColor.fromColor(baseColor);
    final textColor = isDark
        ? hsl.withLightness(hsl.lightness < 0.62 ? 0.62 : hsl.lightness).toColor()
        : hsl.withLightness(hsl.lightness > 0.26 ? 0.26 : hsl.lightness).toColor();

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: baseColor.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(9999),
        border: Border.all(color: baseColor.withValues(alpha: 0.25), width: 1),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: textColor,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
