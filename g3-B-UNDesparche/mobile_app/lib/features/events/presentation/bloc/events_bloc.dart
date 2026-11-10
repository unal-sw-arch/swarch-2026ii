import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/event_entity.dart';
import '../../domain/repositories/events_repository.dart';
import '../../domain/usecases/filter_events.dart';
import 'events_event.dart';
import 'events_state.dart';

/// BLoC que gestiona la consulta, filtrado en vivo, mapa y suscripciones de eventos
class EventsBloc extends Bloc<EventsEvent, EventsState> {
  final EventsRepository eventsRepository;

  EventsBloc({required this.eventsRepository}) : super(const EventsInitial()) {
    on<LoadEventsRequested>(_onLoadEventsRequested);
    on<SearchEventsQueryChanged>(_onSearchEventsQueryChanged);
    on<FilterEventsCategoryChanged>(_onFilterEventsCategoryChanged);
    on<SelectEventRequested>(_onSelectEventRequested);
    on<ShowEventOnMapRequested>(_onShowEventOnMapRequested);
    on<ToggleEventSubscriptionRequested>(_onToggleEventSubscriptionRequested);
  }

  Future<void> _onLoadEventsRequested(
    LoadEventsRequested event,
    Emitter<EventsState> emit,
  ) async {
    emit(const EventsLoading());
    try {
      final events = await eventsRepository.getEvents();
      final filtered = _filter(events, event.category, event.searchQuery ?? '');
      emit(EventsLoaded(
        allEvents: events,
        filteredEvents: filtered,
        selectedCategory: event.category,
        searchQuery: event.searchQuery ?? '',
      ));
    } on Failure catch (failure) {
      emit(EventsError('Error al cargar eventos: ${failure.message}'));
    } catch (e) {
      emit(EventsError('Error al cargar eventos: ${e.toString().replaceAll('Exception: ', '')}'));
    }
  }

  void _onSearchEventsQueryChanged(
    SearchEventsQueryChanged event,
    Emitter<EventsState> emit,
  ) {
    if (state is EventsLoaded) {
      final current = state as EventsLoaded;
      final filtered = _filter(current.allEvents, current.selectedCategory, event.query);
      emit(current.copyWith(
        searchQuery: event.query,
        filteredEvents: filtered,
      ));
    }
  }

  void _onFilterEventsCategoryChanged(
    FilterEventsCategoryChanged event,
    Emitter<EventsState> emit,
  ) {
    if (state is EventsLoaded) {
      final current = state as EventsLoaded;
      final filtered = _filter(current.allEvents, event.category, current.searchQuery);
      emit(current.copyWith(
        selectedCategory: () => event.category,
        filteredEvents: filtered,
      ));
    }
  }

  void _onSelectEventRequested(
    SelectEventRequested event,
    Emitter<EventsState> emit,
  ) {
    if (state is EventsLoaded) {
      final current = state as EventsLoaded;
      emit(current.copyWith(selectedEvent: () => event.event));
    }
  }

  void _onShowEventOnMapRequested(
    ShowEventOnMapRequested event,
    Emitter<EventsState> emit,
  ) {
    if (state is EventsLoaded) {
      final current = state as EventsLoaded;
      emit(current.copyWith(
        selectedEvent: () => event.event,
        mapRequest: () => event.event,
        mapRequestToken: current.mapRequestToken + 1,
      ));
    }
  }

  Future<void> _onToggleEventSubscriptionRequested(
    ToggleEventSubscriptionRequested event,
    Emitter<EventsState> emit,
  ) async {
    if (state is EventsLoaded) {
      final current = state as EventsLoaded;
      try {
        final updated = await eventsRepository.toggleSubscription(
          event.eventId,
          email: event.visitorEmail,
        );

        final all = current.allEvents.map((e) => e.id == updated.id ? updated : e).toList();
        final filtered = _filter(all, current.selectedCategory, current.searchQuery);
        final selected = current.selectedEvent?.id == updated.id ? updated : current.selectedEvent;

        emit(current.copyWith(
          allEvents: all,
          filteredEvents: filtered,
          selectedEvent: () => selected,
        ));
      } catch (e) {
        emit(EventsError('Error al actualizar suscripción: $e'));
      }
    }
  }

  List<EventEntity> _filter(List<EventEntity> list, EventCategory? category, String query) {
    return EventFilter(category: category, query: query).apply(list);
  }
}
