import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/hero_solid_button.dart';
import '../../domain/entities/implement_entity.dart';

/// Dialogo modal que muestra el Descargo de Responsabilidad obligatorio (RF-20 / RF-21)
class DisclaimerDialog extends StatefulWidget {
  final ImplementEntity implement;
  final VoidCallback onConfirm;

  const DisclaimerDialog({
    super.key,
    required this.implement,
    required this.onConfirm,
  });

  static Future<bool?> show(BuildContext context, ImplementEntity implement) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => DisclaimerDialog(
        implement: implement,
        onConfirm: () => Navigator.pop(ctx, true),
      ),
    );
  }

  @override
  State<DisclaimerDialog> createState() => _DisclaimerDialogState();
}

class _DisclaimerDialogState extends State<DisclaimerDialog> {
  bool _accepted = false;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return AlertDialog(
      backgroundColor: isDark ? AppColors.darkSurface : AppColors.lightSurface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      title: Row(
        children: [
          const Icon(Icons.shield_outlined, color: AppColors.mintPrimary),
          const SizedBox(width: 8),
          Expanded(child: Text('Descargo de Responsabilidad', style: context.displaySmall)),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Solicitud de préstamo: ${widget.implement.name}',
              style: context.bodyMedium.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildTermItem('1. Tiempo de Reclamo (RN-02):', 'Tienes un plazo improrrogable de diez (10) minutos para retirar el implemento en ${widget.implement.pickupLocation}.'),
                  const SizedBox(height: 8),
                  _buildTermItem('2. Cuidado del Bien Público:', 'Debes devolver el implemento en las mismas condiciones en que fue entregado dentro del tiempo máximo (${widget.implement.usageTimeMinutes} min).'),
                  const SizedBox(height: 8),
                  _buildTermItem('3. Sanción por Daño (RN-03):', 'En caso de daño o extravío, tu estado cambiará a "Sancionado", suspendiendo futuras reservas hasta reparar el bien.'),
                  const SizedBox(height: 8),
                  _buildTermItem('4. Límite Simultáneo (RN-05):', 'Solo se permite un (1) implemento reservado o en préstamo a la vez por estudiante.'),
                ],
              ),
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              activeColor: AppColors.mintPrimary,
              checkColor: const Color(0xFF032B1D),
              title: Text(
                'Acepto los términos, el reglamento de bienestar y el descargo de responsabilidad (RF-21)',
                style: context.bodySmall.copyWith(fontWeight: FontWeight.w700),
              ),
              value: _accepted,
              onChanged: (val) => setState(() => _accepted = val ?? false),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Cancelar', style: TextStyle(color: AppColors.lightTextSecondary)),
        ),
        HeroSolidButton(
          text: 'Confirmar y Reservar',
          onPressed: _accepted ? widget.onConfirm : null,
        ),
      ],
    );
  }

  Widget _buildTermItem(String title, String desc) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.mintFocus)),
        const SizedBox(height: 2),
        Text(desc, style: const TextStyle(fontSize: 11)),
      ],
    );
  }
}
