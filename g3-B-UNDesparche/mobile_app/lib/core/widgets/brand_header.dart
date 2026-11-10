import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';

/// Cabecera de marca: logo UNAL + nombre a la izquierda, avatar o accion a la derecha.
class BrandHeader extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final Widget? trailing;

  const BrandHeader({super.key, this.title = 'UNDesparche', this.trailing});

  @override
  Size get preferredSize => const Size.fromHeight(64);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final border = isDark ? AppColors.darkBorder : AppColors.lightBorder;

    return Material(
      color: theme.scaffoldBackgroundColor,
      child: Container(
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: border)),
        ),
        child: SafeArea(
          bottom: false,
          child: SizedBox(
            height: preferredSize.height,
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.screenH,
              ),
              child: Row(
                children: [
                  // El archivo assets/icons/unal_logo.png es hoy un placeholder de 1x1 px,
                  // asi que la marca se dibuja con un isotipo menta.
                  const BrandMark(size: 34),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      title,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleLarge,
                    ),
                  ),
                  if (trailing != null) trailing!,
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Isotipo de UNDesparche: icono sobre un cuadrado menta redondeado.
class BrandMark extends StatelessWidget {
  final double size;

  const BrandMark({super.key, this.size = 34});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Semantics(
      label: 'UNDesparche',
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: AppColors.mintTint,
          borderRadius: BorderRadius.circular(size * 0.3),
          border: Border.all(color: AppColors.mintBorder, width: 1.2),
        ),
        child: Icon(
          Icons.diversity_3,
          size: size * 0.55,
          color: isDark ? AppColors.mintLime : AppColors.mintFocus,
        ),
      ),
    );
  }
}

/// Avatar redondo con iniciales. Va en [BrandHeader.trailing].
class InitialsAvatar extends StatelessWidget {
  final String name;
  final VoidCallback? onTap;

  const InitialsAvatar({super.key, required this.name, this.onTap});

  String get _initials {
    final parts = name.trim().split(RegExp(r'\s+')).where((s) => s.isNotEmpty);
    if (parts.isEmpty) return '?';
    final letters = parts.take(2).map((s) => s[0].toUpperCase()).join();
    return letters;
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Semantics(
      button: onTap != null,
      label: 'Perfil de $name',
      child: InkResponse(
        onTap: onTap,
        radius: 28,
        child: SizedBox(
          width: AppSpacing.minTap,
          height: AppSpacing.minTap,
          child: Center(
            child: Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.mintTint,
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.mintBorder),
              ),
              child: Text(
                _initials,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: isDark ? AppColors.mintLime : AppColors.mintFocus,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
