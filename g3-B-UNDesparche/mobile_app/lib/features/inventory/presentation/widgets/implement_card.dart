import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/widgets/campus_tag.dart';
import '../../../../core/widgets/hero_flat_button.dart';
import '../../../../core/widgets/hero_solid_button.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../../../core/widgets/pressable_scale.dart';
import '../../../auth/domain/entities/user_entity.dart';
import '../../domain/entities/implement_entity.dart';

/// Tarjeta que representa un implemento recreativo o deportivo de una facultad UNAL
class ImplementCard extends StatelessWidget {
  final ImplementEntity implement;
  final UserEntity currentUser;
  final VoidCallback onReserve;

  const ImplementCard({
    super.key,
    required this.implement,
    required this.currentUser,
    required this.onReserve,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;
    final accent = isDark ? AppColors.mintLime : AppColors.mintFocus;
    final tag = CampusTag.fromPlace(implement.pickupLocation);

    return PressableScale(
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? AppColors.darkSurface : AppColors.lightSurface,
          borderRadius: AppRadius.cardAll,
          border: Border.all(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.04),
              blurRadius: isDark ? 16 : 8,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Foto 16:9, o fondo menta con icono si no hay foto
            AspectRatio(
              aspectRatio: 16 / 9,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  _buildImage(isDark, accent),
                  Positioned(
                    left: AppSpacing.md,
                    top: AppSpacing.md,
                    child: HeroStatusChip.fromImplementStatus(implement.status),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      if (tag != null) Flexible(child: tag),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: Align(
                          alignment: Alignment.centerRight,
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.timer_outlined, size: 15, color: secondary),
                              const SizedBox(width: 4),
                              Flexible(
                                child: Text(
                                  '${implement.usageTimeMinutes} min de uso',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: secondary),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    implement.name,
                    style: theme.textTheme.titleLarge?.copyWith(fontSize: 18),
                  ),
                  const SizedBox(height: 6),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(top: 1),
                        child: Icon(Icons.place_outlined, size: 16, color: accent),
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          '${implement.facultyName} · ${implement.pickupLocation}',
                          style: TextStyle(fontSize: 13, color: secondary),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    implement.description,
                    style: TextStyle(fontSize: 13, height: 1.4, color: secondary),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  _buildActionButton(context, isDark),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImage(bool isDark, Color accent) {
    final placeholder = Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: isDark
              ? const [Color(0xFF0E1620), Color(0xFF12302B)]
              : const [Color(0xFFE6FAF3), Color(0xFFCDF5E6)],
        ),
      ),
      child: Center(child: Icon(_categoryIcon(implement.category), size: 44, color: accent)),
    );

    final url = implement.imageUrl;
    if (url == null || url.isEmpty) return placeholder;
    return Image.network(
      url,
      width: double.infinity,
      fit: BoxFit.cover,
      errorBuilder: (_, __, ___) => placeholder,
    );
  }

  IconData _categoryIcon(ImplementCategory category) {
    switch (category) {
      case ImplementCategory.balls:
        return Icons.sports_soccer;
      case ImplementCategory.rackets:
        return Icons.sports_tennis;
      case ImplementCategory.boardGames:
        return Icons.extension_outlined;
      case ImplementCategory.leisure:
        return Icons.sports_handball_outlined;
    }
  }

  Widget _buildActionButton(BuildContext context, bool isDark) {
    final secondary = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;

    if (implement.status != ImplementStatus.available) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
        decoration: BoxDecoration(
          color: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.05),
          borderRadius: AppRadius.pillAll,
        ),
        child: Center(
          child: Text(
            implement.status == ImplementStatus.reserved
                ? 'Reservado temporalmente (esperando retiro)'
                : 'Implemento no disponible en este momento',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12.5, color: secondary),
          ),
        ),
      );
    }

    if (currentUser.isGuest) {
      return SizedBox(
        width: double.infinity,
        child: HeroFlatButton(
          text: 'Exclusivo Comunidad UNAL',
          icon: Icons.lock_outline,
          onPressed: () {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Para solicitar implementos debes iniciar sesión con tu correo @unal.edu.co'),
              ),
            );
          },
        ),
      );
    }

    if (currentUser.isSanctioned) {
      return SizedBox(
        width: double.infinity,
        child: HeroFlatButton(
          text: 'Bloqueado por Sanción (RN-03)',
          icon: Icons.block,
          destructive: true,
          onPressed: () {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Tu cuenta está sancionada. Acércate a la taquilla de bienestar de tu facultad.'),
              ),
            );
          },
        ),
      );
    }

    return SizedBox(
      width: double.infinity,
      child: HeroSolidButton(
        text: 'Reservar (10 min)',
        icon: Icons.timer_outlined,
        onPressed: onReserve,
      ),
    );
  }
}
