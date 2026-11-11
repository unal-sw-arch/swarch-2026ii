import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_motion.dart';
import '../../theme/app_spacing.dart';
import '../pressable_scale.dart';

/// Boton cuadrado con el icono de filtros. Muestra un numero cuando hay filtros activos.
class FilterButton extends StatelessWidget {
  final int activeCount;
  final VoidCallback onTap;

  const FilterButton(
      {super.key, required this.activeCount, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final active = activeCount > 0;
    final accent = isDark ? AppColors.mintLime : AppColors.mintInk;
    final border = isDark ? AppColors.darkBorder : AppColors.lightBorder;

    return Semantics(
      button: true,
      label: active ? 'Filtros, $activeCount activos' : 'Filtros',
      excludeSemantics: true,
      child: PressableScale(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          // Ancho fijo; el alto lo pone la fila (igual que el buscador).
          child: SizedBox(
            width: 52,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Positioned.fill(
                  child: AnimatedContainer(
                    duration: AppMotion.ui,
                    curve: AppMotion.curve,
                    decoration: BoxDecoration(
                      color: active
                          ? AppColors.mintTint
                          : (isDark
                              ? AppColors.darkSurface
                              : AppColors.lightSurface),
                      borderRadius: AppRadius.cardAll,
                      border: Border.all(
                          color: active ? AppColors.mintBorder : border),
                    ),
                    child: Icon(Icons.tune,
                        color: active
                            ? accent
                            : (isDark
                                ? AppColors.darkTextSecondary
                                : AppColors.lightTextMuted)),
                  ),
                ),
                if (active)
                  Positioned(
                    top: -2,
                    right: -2,
                    child: Container(
                      constraints:
                          const BoxConstraints(minWidth: 20, minHeight: 20),
                      padding: const EdgeInsets.symmetric(horizontal: 5),
                      alignment: Alignment.center,
                      decoration: const BoxDecoration(
                        color: AppColors.mintPrimary,
                        borderRadius: BorderRadius.all(Radius.circular(10)),
                      ),
                      child: Text(
                        '$activeCount',
                        style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w800,
                            color: Color(0xFF032B1D)),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
