import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import 'hero_solid_button.dart';

/// Estado vacio: icono menta, titulo, texto corto y una accion opcional.
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? description;
  final String? actionLabel;
  final VoidCallback? onAction;

  const EmptyState({
    super.key,
    this.icon = Icons.inbox_outlined,
    required this.title,
    this.description,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary =
        isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: AppColors.mintTint,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.mintBorder),
              ),
              child: Icon(
                icon,
                size: 26,
                color: isDark ? AppColors.mintLime : AppColors.mintFocus,
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            Text(
              title,
              textAlign: TextAlign.center,
              style: theme.textTheme.titleLarge?.copyWith(fontSize: 18),
            ),
            if (description != null) ...[
              const SizedBox(height: 6),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 280),
                child: Text(
                  description!,
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 13, height: 1.4, color: secondary),
                ),
              ),
            ],
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: AppSpacing.lg),
              HeroSolidButton(text: actionLabel!, onPressed: onAction),
            ],
          ],
        ),
      ),
    );
  }
}
