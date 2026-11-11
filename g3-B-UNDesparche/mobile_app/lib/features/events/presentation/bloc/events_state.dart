import 'package:equatable/equatable.dart';
import '../../domain/entities/event_entity.dart';

abstract class EventsState extends Equatable {
  const EventsState();

  @override
  List<Object?> get props => [];
}

/// Estado inicial sin cargar
class EventsInitial extends EventsState {
  const EventsInitial();
}

/// Estado de carga (consultando microservicio / repositorio)
class EventsLoading extends EventsState {
  const EventsLoading();
}

/// Eventos cargados exitosamente
class EventsLoaded extends EventsState {
  final List<EventEntity> allEvents;
  final List<EventEntity> filteredEvents;
  final EventCategory? selectedCategory;
  final String searchQuery;
  final EventEntity? selectedEvent;

  /// Evento que el usuario pidió "Ver en mapa". El shell lo observa para saltar
  /// a la pestaña Mapa, sin pasar un callback dentro de los argumentos de ruta (#9).
  final EventEntity? mapRequest;

  /// Cambia en cada petición de mapa para que el shell distinga dos peticiones
  /// seguidas al mismo evento.
  final int mapRequestToken;

  const EventsLoaded({
    required this.allEvents,
    required this.filteredEvents,
    this.selectedCategory,
    this.searchQuery = '',
    this.selectedEvent,
    this.mapRequest,
    this.mapRequestToken = 0,
  });

  EventsLoaded copyWith({
    List<EventEntity>? allEvents,
    List<EventEntity>? filteredEvents,
    EventCategory? Function()? selectedCategory,
    String? searchQuery,
    EventEntity? Function()? selectedEvent,
    EventEntity? Function()? mapRequest,
    int? mapRequestToken,
  }) {
    return EventsLoaded(
      allEvents: allEvents ?? this.allEvents,
      filteredEvents: filteredEvents ?? this.filteredEvents,
      selectedCategory: selectedCategory != null ? selectedCategory() : this.selectedCategory,
      searchQuery: searchQuery ?? this.searchQuery,
      selectedEvent: selectedEvent != null ? selectedEvent() : this.selectedEvent,
      mapRequest: mapRequest != null ? mapRequest() : this.mapRequest,
      mapRequestToken: mapRequestToken ?? this.mapRequestToken,
    );
  }

  @override
  List<Object?> get props => [
    allEvents,
    filteredEvents,
    selectedCategory,
    searchQuery,
    selectedEvent,
    mapRequest,
    mapRequestToken,
  ];
}

/// Error al consultar eventos
class EventsError extends EventsState {
  final String message;

  const EventsError(this.message);

  @override
  List<Object?> get props => [message];
}
