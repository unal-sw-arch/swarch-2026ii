import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_motion.dart';
import '../theme/app_spacing.dart';

/// Caja gris que respira. Con "menos movimiento" queda quieta.
class SkeletonBox extends StatefulWidget {
  final double? width;
  final double height;
  final BorderRadius? radius;

  const SkeletonBox({super.key, this.width, required this.height, this.radius});

  @override
  State<SkeletonBox> createState() => _SkeletonBoxState();
}

class _SkeletonBoxState extends State<SkeletonBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (!AppMotion.reduced(context)) _c.repeat(reverse: true);
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final base = isDark ? const Color(0xFF16212D) : const Color(0xFFE8EDF2);
    final peak = isDark ? const Color(0xFF1C2937) : const Color(0xFFF1F5F9);
    return ExcludeSemantics(
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, _) => Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            color: Color.lerp(base, peak, _c.value),
            borderRadius: widget.radius ?? BorderRadius.circular(8),
          ),
        ),
      ),
    );
  }
}

/// Esqueleto de una tarjeta de evento: foto 16:9, titulo y dos lineas.
class SkeletonCard extends StatelessWidget {
  final bool withImage;

  const SkeletonCard({super.key, this.withImage = true});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Semantics(
      label: 'Cargando',
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? AppColors.darkSurface : AppColors.lightSurface,
          borderRadius: AppRadius.cardAll,
          border: Border.all(
            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (withImage)
              const AspectRatio(
                aspectRatio: 16 / 9,
                child: SkeletonBox(
                  height: double.infinity,
                  radius: BorderRadius.zero,
                ),
              ),
            const Padding(
              padding: EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SkeletonBox(width: 90, height: 14),
                  SizedBox(height: 12),
                  SkeletonBox(height: 18),
                  SizedBox(height: 8),
                  SkeletonBox(width: 180, height: 14),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Lista de esqueletos para el estado de carga.
class SkeletonList extends StatelessWidget {
  final int count;
  final bool withImage;

  const SkeletonList({super.key, this.count = 3, this.withImage = true});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      physics: const NeverScrollableScrollPhysics(),
      padding: EdgeInsets.fromLTRB(
        AppSpacing.screenH,
        AppSpacing.screenH,
        AppSpacing.screenH,
        AppSpacing.screenH + MediaQuery.paddingOf(context).bottom,
      ),
      itemCount: count,
      separatorBuilder: (_, __) => const SizedBox(height: AppSpacing.md),
      itemBuilder: (_, __) => SkeletonCard(withImage: withImage),
    );
  }
}
