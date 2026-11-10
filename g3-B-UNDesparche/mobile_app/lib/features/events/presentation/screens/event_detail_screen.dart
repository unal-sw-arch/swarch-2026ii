import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/widgets/campus_tag.dart';
import '../../../../core/widgets/fade_up_in.dart';
import '../../../../core/widgets/hero_solid_button.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../../../core/widgets/hero_text_field.dart';
import '../../../auth/domain/entities/user_entity.dart';
import '../../domain/entities/event_entity.dart';
import '../bloc/events_bloc.dart';
import '../bloc/events_event.dart';
import '../bloc/events_state.dart';
import '../widgets/event_card.dart';
import '../widgets/event_labels.dart';

/// Pantalla de Detalle de Evento con suscripcion para estudiantes y visitantes (RF-11 / RF-12)
class EventDetailScreen extends StatefulWidget {
  final EventEntity event;
  final UserEntity currentUser;

  const EventDetailScreen({
    super.key,
    required this.event,
    required this.currentUser,
  });

  @override
  State<EventDetailScreen> createState() => _EventDetailScreenState();
}

class _EventDetailScreenState extends State<EventDetailScreen> {
  late EventEntity _event;
  bool _isSubscribing = false;
  final TextEditingController _visitorEmailController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _event = widget.event;
  }

  @override
  void didUpdateWidget(covariant EventDetailScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.event != oldWidget.event) {
      _event = widget.event;
    }
  }

  @override
  void dispose() {
    _visitorEmailController.dispose();
    super.dispose();
  }

  void _showVisitorSubscribeDialog() {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        title: Text(
          'Suscripción por correo',
          style: Theme.of(dialogCtx).textTheme.titleLarge,
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Como visitante externo, puedes registrar tu correo para recibir novedades y cambios de este evento:',
              style: TextStyle(
                fontSize: 13,
                height: 1.4,
                color: isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary,
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            HeroTextField(
              controller: _visitorEmailController,
              keyboardType: TextInputType.emailAddress,
              hintText: 'tu_correo@ejemplo.com',
              prefixIcon: Icons.alternate_email,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx),
            child: const Text('Cancelar'),
          ),
          HeroSolidButton(
            text: 'Suscribirme',
            onPressed: () {
              final email = _visitorEmailController.text.trim();
              if (email.contains('@')) {
                Navigator.pop(dialogCtx);
                setState(() => _isSubscribing = true);
                context.read<EventsBloc>().add(
                      ToggleEventSubscriptionRequested(
                        eventId: _event.id,
                        visitorEmail: email,
                      ),
                    );
              }
            },
          ),
        ],
      ),
    );
  }

  void _toggleSubscription() {
    if (_isSubscribing) return;
    if (widget.currentUser.isGuest && !_event.isSubscribed) {
      _showVisitorSubscribeDialog();
      return;
    }
    setState(() => _isSubscribing = true);
    context.read<EventsBloc>().add(
          ToggleEventSubscriptionRequested(
            eventId: _event.id,
            visitorEmail: widget.currentUser.email,
          ),
        );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;
    final dateFmt = DateFormat("EEEE d 'de' MMMM · h:mm a", 'es');
    final tag = CampusTag.fromPlace(_event.venueName);

    return BlocListener<EventsBloc, EventsState>(
      listener: (context, state) {
        if (state is EventsLoaded) {
          final updated = state.allEvents.where((e) => e.id == _event.id).firstOrNull;
          if (updated != null && updated != _event) {
            final wasSubscribed = _event.isSubscribed;
            final isNowSubscribed = updated.isSubscribed;
            setState(() {
              _event = updated;
              _isSubscribing = false;
            });
            if (wasSubscribed != isNowSubscribed) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    isNowSubscribed
                        ? '¡Te has suscrito al evento!'
                        : 'Suscripción cancelada',
                  ),
                ),
              );
            }
          } else if (_isSubscribing) {
            setState(() => _isSubscribing = false);
          }
        } else if (state is EventsError) {
          setState(() => _isSubscribing = false);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              backgroundColor: AppColors.danger,
              content: Text(state.message),
            ),
          );
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Detalle del Evento'),
          actions: [
            _isSubscribing
                ? const Center(
                    child: Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16),
                      child: SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2.2),
                      ),
                    ),
                  )
                : IconButton(
                    icon: Icon(
                      _event.isSubscribed ? Icons.bookmark : Icons.bookmark_border,
                      color: _event.isSubscribed ? AppColors.mintPrimary : null,
                    ),
                    tooltip: _event.isSubscribed ? 'Cancelar suscripción' : 'Suscribirme',
                    onPressed: _toggleSubscription,
                  ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(AppSpacing.screenH),
          children: [
            // Foto (16:9) o fondo menta con el icono de la categoria
            FadeUpIn(
              child: ClipRRect(
                borderRadius: AppRadius.cardAll,
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: EventImage(event: _event),
                ),
              ),
            ),

            const SizedBox(height: AppSpacing.lg),

            // Categoria, estado y edificio
            FadeUpIn(
              index: 1,
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  HeroStatusChip(
                    label: eventCategoryLabel(_event.category),
                    baseColor: AppColors.mintPrimary,
                  ),
                  HeroStatusChip(
                    label: eventStatusLabel(_event.status),
                    baseColor: _event.status == EventStatus.inProgress
                        ? AppColors.warning
                        : (_event.status == EventStatus.cancelled ? AppColors.danger : AppColors.occupied),
                  ),
                  if (_event.isSubscribed)
                    const HeroStatusChip(label: 'Suscrito', baseColor: AppColors.success),
                  if (tag != null) tag,
                ],
              ),
            ),

            const SizedBox(height: AppSpacing.md),

            FadeUpIn(
              index: 2,
              child: Text(
                _event.title,
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.3,
                ),
              ),
            ),

            const SizedBox(height: AppSpacing.lg),

            // Fecha, lugar y organizador
            FadeUpIn(
              index: 3,
              child: Container(
                padding: const EdgeInsets.all(AppSpacing.lg),
                decoration: BoxDecoration(
                  color: isDark ? AppColors.darkSurface : AppColors.lightSurface,
                  borderRadius: AppRadius.cardAll,
                  border: Border.all(color: isDark ? AppColors.darkBorder : AppColors.lightBorder),
                ),
                child: Column(
                  children: [
                    _InfoRow(
                      icon: Icons.calendar_today_outlined,
                      label: 'Fecha y hora',
                      value: dateFmt.format(_event.startDateTime),
                    ),
                    const Divider(height: 24),
                    _InfoRow(
                      icon: Icons.location_on_outlined,
                      label: 'Lugar en el campus',
                      value: _event.venueName,
                    ),
                    const Divider(height: 24),
                    _InfoRow(
                      icon: Icons.person_outline,
                      label: 'Organizado por',
                      value: _event.organizer,
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: AppSpacing.lg),

            // Descripcion
            FadeUpIn(
              index: 4,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Descripción',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  Text(
                    _event.description,
                    style: TextStyle(
                      fontSize: 14,
                      height: 1.5,
                      color: secondary,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: AppSpacing.xl),

            // Boton principal de suscripcion
            HeroSolidButton(
              isLoading: _isSubscribing,
              text: _event.isSubscribed
                  ? 'Cancelar Suscripción'
                  : (widget.currentUser.isGuest ? 'Suscribirme como Visitante' : 'Suscribirme al Evento'),
              icon: _event.isSubscribed ? Icons.bookmark_remove : Icons.notifications_active,
              onPressed: _toggleSubscription,
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Widget? action;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.action,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final secondary = isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AppColors.mintTint,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: AppColors.mintBorder),
          ),
          child: Icon(icon, size: 18, color: isDark ? AppColors.mintLime : AppColors.mintFocus),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: TextStyle(fontSize: 12, color: secondary)),
              const SizedBox(height: 2),
              Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
            ],
          ),
        ),
        if (action != null) action!,
      ],
    );
  }
}
