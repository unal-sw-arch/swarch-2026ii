import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';
import 'pressable_scale.dart';

/// Boton secundario en pildora: borde fino, sin relleno. Para acciones menos importantes.
class HeroFlatButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final IconData? icon;

  /// Rojo para acciones que cancelan o borran.
  final bool destructive;

  const HeroFlatButton({
    super.key,
    required this.text,
    this.onPressed,
    this.icon,
    this.destructive = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final border = isDark ? AppColors.darkBorder : AppColors.lightBorder;
    final fg = destructive
        ? AppColors.danger
        : (isDark ? AppColors.darkTextPrimary : AppColors.lightTextPrimary);
    final enabled = onPressed != null;

    return PressableScale(
      enabled: enabled,
      child: Opacity(
        opacity: enabled ? 1 : 0.5,
        child: Material(
          color: Colors.transparent,
          shape: StadiumBorder(side: BorderSide(color: border)),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: AppSpacing.minTap),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 22),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (icon != null) ...[
                      Icon(icon, size: 18, color: fg),
                      const SizedBox(width: 8),
                    ],
                    Flexible(
                      child: Text(
                        text,
                        overflow: TextOverflow.ellipsis,
                        style: AppTextStyles.buttonSolid(Theme.of(context).textTheme.bodyLarge).copyWith(color: fg),
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
