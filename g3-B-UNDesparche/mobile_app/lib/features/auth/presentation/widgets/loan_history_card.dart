import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/hero_card.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../../inventory/domain/entities/loan_record_entity.dart';

/// Tarjeta de un registro del historial de préstamos (RF-22).
class LoanHistoryCard extends StatelessWidget {
  final LoanRecordEntity loan;

  const LoanHistoryCard({super.key, required this.loan});

  (Color, String) _status() {
    switch (loan.status) {
      case LoanStatus.returnedOnTime:
        return (AppColors.success, 'DEVUELTO A TIEMPO');
      case LoanStatus.returnedLate:
        return (AppColors.warning, 'DEVUELTO CON RETRASO');
      case LoanStatus.damaged:
        return (AppColors.danger, 'DAÑADO / SANCIÓN (RN-03)');
      case LoanStatus.active:
        return (AppColors.occupied, 'EN USO');
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateFmt = DateFormat("d 'de' MMMM · h:mm a", 'es');
    final (statusColor, statusLabel) = _status();

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: HeroCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    loan.implementName,
                    style: context.displaySmall.copyWith(fontSize: 15),
                  ),
                ),
                HeroStatusChip(label: statusLabel, baseColor: statusColor),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                const Icon(Icons.place_outlined, size: 14, color: AppColors.mintFocus),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    '${loan.pickupLocation} (${loan.facultyName})',
                    style: context.bodySmall,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 2),
            Row(
              children: [
                const Icon(Icons.schedule, size: 14, color: AppColors.mintFocus),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    'Retirado: ${dateFmt.format(loan.borrowedAt)}',
                    style: context.bodySmall,
                  ),
                ),
              ],
            ),
            if (loan.penaltyNote != null) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: AppColors.danger.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.danger.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.report_problem, size: 16, color: AppColors.danger),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        loan.penaltyNote!,
                        style: context.bodySmall.copyWith(
                          color: AppColors.danger,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
