import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_motion.dart';
import '../theme/app_spacing.dart';
import 'pressable_scale.dart';

/// Filtro en pildora. Activo: tinte menta, borde menta, texto menta.
class HeroFilterPill extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback? onTap;
  final IconData? icon;

  const HeroFilterPill({
    super.key,
    required this.label,
    required this.selected,
    this.onTap,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accent = isDark ? AppColors.mintLime : AppColors.mintInk;
    final idleBorder = isDark ? AppColors.darkBorder : AppColors.lightBorder;
    final idleText =
        isDark ? AppColors.darkTextSecondary : AppColors.lightTextMuted;
    final fg = selected ? accent : idleText;

    return Semantics(
      button: true,
      selected: selected,
      label: label,
      child: PressableScale(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          child: ConstrainedBox(
            // La zona de toque llega a 48 px aunque la pildora sea mas baja.
            constraints: const BoxConstraints(minHeight: AppSpacing.minTap),
            child: Center(
              widthFactor: 1,
              child: AnimatedContainer(
                duration: AppMotion.ui,
                curve: AppMotion.curve,
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: selected ? AppColors.mintTint : Colors.transparent,
                  borderRadius: AppRadius.pillAll,
                  border: Border.all(
                    color: selected ? AppColors.mintBorder : idleBorder,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (icon != null) ...[
                      Icon(icon, size: 16, color: fg),
                      const SizedBox(width: 6),
                    ],
                    Text(
                      label,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight:
                            selected ? FontWeight.w700 : FontWeight.w600,
                        color: fg,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
