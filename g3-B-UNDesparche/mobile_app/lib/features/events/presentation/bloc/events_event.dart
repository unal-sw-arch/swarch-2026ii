import 'package:equatable/equatable.dart';
import '../../domain/entities/event_entity.dart';

abstract class EventsEvent extends Equatable {
  const EventsEvent();

  @override
  List<Object?> get props => [];
}

/// Solicita cargar los eventos (con filtros opcionales de categoria y busqueda)
class LoadEventsRequested extends EventsEvent {
  final EventCategory? category;
  final String? searchQuery;

  const LoadEventsRequested({this.category, this.searchQuery});

  @override
  List<Object?> get props => [category, searchQuery];
}

/// Actualiza la busqueda de texto en vivo
class SearchEventsQueryChanged extends EventsEvent {
  final String query;

  const SearchEventsQueryChanged(this.query);

  @override
  List<Object?> get props => [query];
}

/// Cambia el filtro de categoria
class FilterEventsCategoryChanged extends EventsEvent {
  final EventCategory? category;

  const FilterEventsCategoryChanged(this.category);

  @override
  List<Object?> get props => [category];
}

/// Selecciona un evento para resaltarlo en el mapa o ver su detalle
class SelectEventRequested extends EventsEvent {
  final EventEntity? event;

  const SelectEventRequested(this.event);

  @override
  List<Object?> get props => [event];
}

/// Pide mostrar un evento en la pestaña Mapa. El shell lo observa y salta a Mapa (#9).
class ShowEventOnMapRequested extends EventsEvent {
  final EventEntity event;

  const ShowEventOnMapRequested(this.event);

  @override
  List<Object?> get props => [event];
}

/// Alterna la suscripcion de un estudiante o visitante al evento (RF-11 / RF-12)
class ToggleEventSubscriptionRequested extends EventsEvent {
  final String eventId;
  final String? visitorEmail;

  const ToggleEventSubscriptionRequested({
    required this.eventId,
    this.visitorEmail,
  });

  @override
  List<Object?> get props => [eventId, visitorEmail];
}
