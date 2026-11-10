import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/campus_tag.dart';
import '../../../../core/widgets/hero_flat_button.dart';
import '../../../../core/widgets/hero_solid_button.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../domain/entities/reservation_entity.dart';

/// Banner reactivo para la reserva activa con temporizador de 10 minutos (RN-02).
/// Es lo unico llamativo de la app: el reloj grande en Varela Round.
class ActiveReservationBanner extends StatelessWidget {
  final ReservationEntity reservation;
  final int remainingSeconds;
  final VoidCallback onCancel;

  const ActiveReservationBanner({
    super.key,
    required this.reservation,
    required this.remainingSeconds,
    required this.onCancel,
  });

  void _showPickupCodeModal(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;

    // Forma, color y manija vienen de bottomSheetTheme.
    showModalBottomSheet(
      context: context,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.fromLTRB(AppSpacing.xl, AppSpacing.sm, AppSpacing.xl, AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Turno de Entrega en Taquilla', style: theme.textTheme.headlineSmall),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Presenta este código y tu carné estudiantil físico en ${reservation.pickupLocation}:',
              style: TextStyle(fontSize: 13, height: 1.4, color: secondary),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.xl),
            // Codigo / Token de Retiro
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              decoration: BoxDecoration(
                color: AppColors.mintTint,
                borderRadius: AppRadius.cardAll,
                border: Border.all(color: AppColors.mintBorder),
              ),
              child: Text(
                'UNAL-${reservation.id.substring(reservation.id.length - 6).toUpperCase()}',
                style: AppTextStyles.timerDigits(Theme.of(context).textTheme.displaySmall).copyWith(
                  fontSize: 28,
                  letterSpacing: 4,
                  color: isDark ? AppColors.mintLime : AppColors.mintFocus,
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            SizedBox(
              width: double.infinity,
              child: HeroSolidButton(
                text: 'Cerrar',
                onPressed: () => Navigator.pop(ctx),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;
    final minutes = (remainingSeconds ~/ 60).toString().padLeft(2, '0');
    final seconds = (remainingSeconds % 60).toString().padLeft(2, '0');
    final progress = (remainingSeconds / 600.0).clamp(0.0, 1.0);
    final urgent = remainingSeconds < 180;
    final timerColor = urgent ? AppColors.danger : AppColors.warning;
    final tag = CampusTag.fromPlace(reservation.pickupLocation);

    return Semantics(
      label: 'Reserva activa. Quedan $minutes minutos con $seconds segundos.',
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: AppColors.warning.withValues(alpha: isDark ? 0.10 : 0.08),
          borderRadius: AppRadius.cardAll,
          border: Border.all(color: AppColors.warning.withValues(alpha: 0.30)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const HeroStatusChip(label: 'RESERVA ACTIVA (RN-02)', baseColor: AppColors.warning),
                const Spacer(),
                if (tag != null) tag,
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            // El reloj es el protagonista
            Center(
              child: ExcludeSemantics(
                child: Text(
                  '$minutes:$seconds',
                  style: AppTextStyles.timerDigits(Theme.of(context).textTheme.displaySmall).copyWith(fontSize: 52, color: timerColor),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 6,
                backgroundColor: AppColors.warning.withValues(alpha: 0.2),
                valueColor: AlwaysStoppedAnimation<Color>(timerColor),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text(reservation.implementName, style: theme.textTheme.titleLarge?.copyWith(fontSize: 17)),
            const SizedBox(height: 4),
            Row(
              children: [
                const Icon(Icons.place_outlined, size: 16, color: AppColors.warning),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    'Reclama en: ${reservation.pickupLocation}',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: secondary),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Row(
              children: [
                Expanded(
                  child: HeroSolidButton(
                    text: 'Mi código',
                    icon: Icons.qr_code,
                    onPressed: () => _showPickupCodeModal(context),
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: HeroFlatButton(
                    text: 'Cancelar',
                    icon: Icons.cancel_outlined,
                    destructive: true,
                    onPressed: onCancel,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
