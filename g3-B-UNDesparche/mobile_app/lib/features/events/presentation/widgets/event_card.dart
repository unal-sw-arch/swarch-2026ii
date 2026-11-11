import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/widgets/campus_tag.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../../../core/widgets/pressable_scale.dart';
import '../../domain/entities/event_entity.dart';
import 'event_labels.dart';

/// Foto del evento (16:9) o, si no hay foto, un fondo menta con el icono de la categoría.
class EventImage extends StatelessWidget {
  final EventEntity event;
  final double? height;

  const EventImage({super.key, required this.event, this.height});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final placeholder = Container(
      height: height,
      width: double.infinity,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: isDark
              ? const [Color(0xFF0E1620), Color(0xFF12302B)]
              : const [Color(0xFFE6FAF3), Color(0xFFCDF5E6)],
        ),
      ),
      child: Center(
        child: Icon(
          eventCategoryIcon(event.category),
          size: 44,
          color: isDark ? AppColors.mintLime : AppColors.mintFocus,
        ),
      ),
    );

    final url = event.imageUrl;
    if (url == null || url.isEmpty) return placeholder;
    return Image.network(
      url,
      height: height,
      width: double.infinity,
      fit: BoxFit.cover,
      errorBuilder: (_, __, ___) => placeholder,
    );
  }
}

/// Tarjeta de evento como `EventCard.tsx`: foto con la categoría encima,
/// etiqueta de edificio, título, fecha y lugar.
class EventCard extends StatelessWidget {
  final EventEntity event;
  final VoidCallback onTap;
  final VoidCallback onToggleSubscription;

  const EventCard({
    super.key,
    required this.event,
    required this.onTap,
    required this.onToggleSubscription,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary =
        isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;
    final dateFmt = DateFormat("EEE d MMM · h:mm a", 'es');
    final tag = CampusTag.fromPlace(event.venueName);

    return PressableScale(
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? AppColors.darkSurface : AppColors.lightSurface,
          borderRadius: AppRadius.cardAll,
          border: Border.all(
            color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.04),
              blurRadius: isDark ? 16 : 8,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onTap,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                AspectRatio(
                  aspectRatio: 16 / 9,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      EventImage(event: event),
                      Positioned(
                        left: AppSpacing.md,
                        top: AppSpacing.md,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.55),
                            borderRadius: AppRadius.pillAll,
                          ),
                          child: Text(
                            eventCategoryLabel(event.category),
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 11.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                      Positioned(
                        right: 4,
                        top: 4,
                        child: IconButton(
                          tooltip: event.isSubscribed
                              ? 'Cancelar suscripción'
                              : 'Suscribirme',
                          style: IconButton.styleFrom(
                            backgroundColor:
                                Colors.black.withValues(alpha: 0.45),
                            minimumSize: const Size.square(AppSpacing.minTap - 8),
                          ),
                          icon: Icon(
                            event.isSubscribed
                                ? Icons.bookmark
                                : Icons.bookmark_border,
                            color: event.isSubscribed
                                ? AppColors.mintPrimary
                                : Colors.white,
                          ),
                          onPressed: onToggleSubscription,
                        ),
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
                          if (tag != null) tag,
                          if (event.status == EventStatus.inProgress) ...[
                            const SizedBox(width: 8),
                            HeroStatusChip(
                              label: eventStatusLabel(event.status),
                              baseColor: AppColors.warning,
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        event.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleLarge?.copyWith(fontSize: 18),
                      ),
                      const SizedBox(height: 8),
                      _MetaRow(
                        icon: Icons.schedule,
                        text: dateFmt.format(event.startDateTime),
                        color: secondary,
                      ),
                      const SizedBox(height: 4),
                      _MetaRow(
                        icon: Icons.place_outlined,
                        text: event.venueName,
                        color: secondary,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _MetaRow extends StatelessWidget {
  final IconData icon;
  final String text;
  final Color color;

  const _MetaRow({required this.icon, required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Row(
      children: [
        Icon(icon, size: 16, color: isDark ? AppColors.mintLime : AppColors.mintFocus),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 13, color: color),
          ),
        ),
      ],
    );
  }
}
