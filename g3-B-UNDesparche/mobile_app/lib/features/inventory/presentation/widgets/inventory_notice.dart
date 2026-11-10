import 'package:flutter/material.dart';
import '../../../../core/theme/app_spacing.dart';

/// Aviso en una línea con icono y color de estado (visitante, sanción).
class InventoryNotice extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String text;

  const InventoryNotice({
    super.key,
    required this.icon,
    required this.color,
    required this.text,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: AppRadius.cardAll,
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 22),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Text(text, style: const TextStyle(fontSize: 13, height: 1.4)),
          ),
        ],
      ),
    );
  }
}
