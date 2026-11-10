import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import 'filter_group.dart';

/// Fichas con los filtros puestos. Tocar una la quita. No ocupa espacio si no hay filtros.
class ActiveFilterChips extends StatelessWidget {
  final List<FilterGroup> groups;
  final void Function(String groupId) onRemove;

  const ActiveFilterChips(
      {super.key, required this.groups, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    final active = groups.where((g) => g.isActive).toList();
    if (active.isEmpty) return const SizedBox.shrink();

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final accent = isDark ? AppColors.mintLime : AppColors.mintInk;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.screenH),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Wrap(
          spacing: AppSpacing.sm,
          children: [
            for (final group in active)
              Semantics(
                button: true,
                label: 'Quitar filtro ${group.selectedLabel}',
                excludeSemantics: true,
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => onRemove(group.id),
                  child: ConstrainedBox(
                    constraints:
                        const BoxConstraints(minHeight: AppSpacing.minTap),
                    child: Center(
                      widthFactor: 1,
                      child: Container(
                        padding: const EdgeInsets.only(
                            left: 12, right: 8, top: 6, bottom: 6),
                        decoration: BoxDecoration(
                          color: AppColors.mintTint,
                          borderRadius: AppRadius.pillAll,
                          border: Border.all(color: AppColors.mintBorder),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              group.selectedLabel,
                              style: TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                  color: accent),
                            ),
                            const SizedBox(width: 4),
                            Icon(Icons.close, size: 16, color: accent),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
