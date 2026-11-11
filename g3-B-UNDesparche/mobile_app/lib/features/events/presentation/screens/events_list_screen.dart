import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/router/app_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/widgets/brand_header.dart';
import '../../../../core/widgets/empty_state.dart';
import '../../../../core/widgets/fade_up_in.dart';
import '../../../../core/widgets/filter_sheet.dart';
import '../../../../core/widgets/hero_text_field.dart';
import '../../../../core/widgets/skeleton_card.dart';
import '../../../auth/domain/entities/user_entity.dart';
import '../../domain/entities/event_entity.dart';
import '../../domain/usecases/filter_events.dart';
import '../bloc/events_bloc.dart';
import '../bloc/events_event.dart';
import '../bloc/events_state.dart';
import '../widgets/event_card.dart';
import '../widgets/event_labels.dart';

/// Pantalla desacoplada del feed y explorador de eventos en el campus (RF-4, RF-5, RF-6)
class EventsListScreen extends StatefulWidget {
  final UserEntity currentUser;

  const EventsListScreen({super.key, required this.currentUser});

  @override
  State<EventsListScreen> createState() => _EventsListScreenState();
}

class _EventsListScreenState extends State<EventsListScreen> {
  final TextEditingController _searchEventsController = TextEditingController();

  static const List<EventCategory> _filterCategories = [
    EventCategory.sports,
    EventCategory.culture,
    EventCategory.academic,
  ];

  List<FilterGroup> _groups(EventsLoaded state) => [
        FilterGroup(
          id: 'category',
          title: 'Categoría',
          defaultKey: 'all',
          selectedKey: state.selectedCategory?.name ?? 'all',
          options: [
            const FilterOption('all', 'Todos', icon: Icons.apps),
            for (final c in _filterCategories)
              FilterOption(c.name, eventCategoryLabel(c),
                  icon: eventCategoryIcon(c)),
          ],
        ),
      ];

  Future<void> _openFilters(
      BuildContext context, List<FilterGroup> groups) async {
    final state = context.read<EventsBloc>().state;
    final result = await showFilterSheet(
      context,
      groups: groups,
      resultNoun: ('evento', 'eventos'),
      resultCount: state is EventsLoaded
          ? (sel) => _countFor(state, sel['category']!)
          : null,
    );
    if (result == null || !context.mounted) return;
    _applyCategory(context, result['category']!);
  }

  /// Cuantos eventos quedarian con esa categoria y la busqueda actual (misma regla que el bloc).
  int _countFor(EventsLoaded state, String categoryKey) {
    final filter = EventFilter(
      category:
          categoryKey == 'all' ? null : EventCategory.values.byName(categoryKey),
      query: state.searchQuery,
    );
    return filter.count(state.allEvents);
  }

  void _applyCategory(BuildContext context, String key) {
    context.read<EventsBloc>().add(
          FilterEventsCategoryChanged(
              key == 'all' ? null : EventCategory.values.byName(key)),
        );
  }

  @override
  void dispose() {
    _searchEventsController.dispose();
    super.dispose();
  }

  PreferredSizeWidget _header(BuildContext context) {
    return BrandHeader(
      title: 'Eventos',
      trailing: InitialsAvatar(name: widget.currentUser.fullName),
    );
  }

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<EventsBloc, EventsState>(
      builder: (context, state) {
        if (state is EventsLoaded) {
          return Scaffold(
            appBar: _header(context),
            body: Column(
              children: [
                // Busqueda y boton de filtros
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.screenH,
                    AppSpacing.md,
                    AppSpacing.screenH,
                    AppSpacing.xs,
                  ),
                  child: IntrinsicHeight(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(
                          child: HeroTextField.search(
                            controller: _searchEventsController,
                            hintText:
                                'Buscar evento o lugar (ej. Canchas, CyT)...',
                            onChanged: (val) {
                              context
                                  .read<EventsBloc>()
                                  .add(SearchEventsQueryChanged(val));
                            },
                            onClear: () {
                              context
                                  .read<EventsBloc>()
                                  .add(const SearchEventsQueryChanged(''));
                            },
                          ),
                        ),
                        const SizedBox(width: AppSpacing.sm),
                        FilterButton(
                          activeCount: activeFilterCount(_groups(state)),
                          onTap: () => _openFilters(context, _groups(state)),
                        ),
                      ],
                    ),
                  ),
                ),

                // Filtros puestos (tocar uno lo quita)
                ActiveFilterChips(
                  groups: _groups(state),
                  onRemove: (_) => _applyCategory(context, 'all'),
                ),
                Expanded(
                  child: RefreshIndicator(
                    color: AppColors.mintPrimary,
                    onRefresh: () async {
                      context
                          .read<EventsBloc>()
                          .add(const LoadEventsRequested());
                    },
                    child: state.filteredEvents.isEmpty
                        ? ListView(
                            physics: const AlwaysScrollableScrollPhysics(),
                            children: [
                              SizedBox(
                                height: 360,
                                child: EmptyState(
                                  icon: Icons.event_busy_outlined,
                                  title: 'Sin eventos',
                                  description:
                                      'No hay eventos que coincidan con la búsqueda.',
                                  actionLabel: 'Ver todos',
                                  onAction: () {
                                    _searchEventsController.clear();
                                    context.read<EventsBloc>().add(
                                        const SearchEventsQueryChanged(''));
                                    context.read<EventsBloc>().add(
                                        const FilterEventsCategoryChanged(
                                            null));
                                  },
                                ),
                              ),
                            ],
                          )
                        : ListView.separated(
                            physics: const AlwaysScrollableScrollPhysics(),
                            padding: EdgeInsets.fromLTRB(
                              AppSpacing.screenH,
                              AppSpacing.screenH,
                              AppSpacing.screenH,
                              AppSpacing.screenH +
                                  MediaQuery.paddingOf(context).bottom,
                            ),
                            itemCount: state.filteredEvents.length,
                            separatorBuilder: (_, __) =>
                                const SizedBox(height: AppSpacing.md),
                            itemBuilder: (context, index) {
                              final event = state.filteredEvents[index];
                              return FadeUpIn(
                                index: index,
                                child: EventCard(
                                  event: event,
                                  onTap: () {
                                    context.push(
                                      '/events/${event.id}',
                                      extra: EventDetailArgs(
                                        event: event,
                                        currentUser: widget.currentUser,
                                        eventsBloc: context.read<EventsBloc>(),
                                      ),
                                    );
                                  },
                                  onToggleSubscription: () {
                                    context.read<EventsBloc>().add(
                                          ToggleEventSubscriptionRequested(
                                            eventId: event.id,
                                            visitorEmail:
                                                widget.currentUser.email,
                                          ),
                                        );
                                  },
                                ),
                              );
                            },
                          ),
                  ),
                ),
              ],
            ),
          );
        }

        // Cargando (o aun sin datos): esqueletos en lugar de un spinner
        return Scaffold(
          appBar: _header(context),
          body: const SkeletonList(),
        );
      },
    );
  }
}
