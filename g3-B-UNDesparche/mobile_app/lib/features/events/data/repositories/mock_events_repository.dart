import 'dart:async';
import '../../../../core/config/app_config.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/event_entity.dart';
import '../../domain/repositories/events_repository.dart';

/// Implementacion mockeada de [EventsRepository] con datos reales del Campus Bogota UNAL.
/// Simula latencia de red para validar estados de carga (skeletons y spinners).
class MockEventsRepository implements EventsRepository {
  final List<EventEntity> _events = [
    EventEntity(
      id: 'ev-001',
      title: 'Torneo Relampago de Microfutbol Interfacultades',
      description: 'Torneo corto de fin de semana para estudiantes y egresados. Se premiaran los tres primeros puestos.',
      category: EventCategory.sports,
      venueName: 'Canchas de Minas (Ed. 411)',
      latitude: 4.6391,
      longitude: -74.0858,
      startDateTime: DateTime.now().add(const Duration(hours: 4)),
      endDateTime: DateTime.now().add(const Duration(hours: 8)),
      organizer: 'Bienestar Facultad de Ingenieria',
      status: EventStatus.scheduled,
      imageUrl: 'https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=600&q=80',
      isSubscribed: false,
    ),
    EventEntity(
      id: 'ev-002',
      title: 'Ciclo de Cine Club: Joyas del Cine Latinoamericano',
      description: 'Proyeccion de peliculas independientes con debate guiado al finalizar. Entrada libre para comunidad y visitantes.',
      category: EventCategory.culture,
      venueName: 'Auditorio Leon de Greiff / La Perla (Ed. 104)',
      latitude: 4.6366,
      longitude: -74.0827,
      startDateTime: DateTime.now().add(const Duration(days: 1, hours: 2)),
      endDateTime: DateTime.now().add(const Duration(days: 1, hours: 5)),
      organizer: 'Direccion de Divulgacion Cultural UNAL',
      status: EventStatus.scheduled,
      imageUrl: 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=600&q=80',
      isSubscribed: true,
    ),
    EventEntity(
      id: 'ev-003',
      title: 'Feria de Semilleros e Innovacion Tecnologica',
      description: 'Exposicion de proyectos de software, robotica y prototipado desarrollados por grupos de investigacion.',
      category: EventCategory.academic,
      venueName: 'Edificio CyT - Ciencia y Tecnologia (Ed. 453)',
      latitude: 4.6382,
      longitude: -74.0882,
      startDateTime: DateTime.now().add(const Duration(days: 2)),
      endDateTime: DateTime.now().add(const Duration(days: 2, hours: 6)),
      organizer: 'Semillero de Ingenieria de Sistemas',
      status: EventStatus.scheduled,
      imageUrl: 'https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=600&q=80',
      isSubscribed: false,
    ),
    EventEntity(
      id: 'ev-004',
      title: 'Encuentro Acustico y Tambores al Aire Libre',
      description: 'Espacio de integracion musical libre. Trae tus instrumentos de percusion o cuerdas.',
      category: EventCategory.culture,
      venueName: 'Jardin Freud / El Frepe (Ed. 224)',
      latitude: 4.6358,
      longitude: -74.0845,
      startDateTime: DateTime.now().add(const Duration(hours: 1)),
      endDateTime: DateTime.now().add(const Duration(hours: 3)),
      organizer: 'Colectivo Estudiantil de Artes',
      status: EventStatus.inProgress,
      imageUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80',
      isSubscribed: false,
    ),
    EventEntity(
      id: 'ev-005',
      title: 'Taller de Aperturas y Estrategia en Ajedrez',
      description: 'Sesion practica guiada por la seleccion de ajedrez UNAL para todos los niveles de juego.',
      category: EventCategory.sports,
      venueName: 'Taquilla Bienestar Central (Ed. 412 Polideportivo)',
      latitude: 4.6375,
      longitude: -74.0850,
      startDateTime: DateTime.now().add(const Duration(days: 3, hours: 4)),
      endDateTime: DateTime.now().add(const Duration(days: 3, hours: 6)),
      organizer: 'Area de Actividad Fisica y Deporte',
      status: EventStatus.scheduled,
      imageUrl: 'https://images.unsplash.com/photo-1529699211952-734e80c4d42b?auto=format&fit=crop&w=600&q=80',
      isSubscribed: false,
    ),
  ];

  @override
  Future<List<EventEntity>> getEvents({
    EventCategory? category,
    String? searchQuery,
  }) async {
    // Simular retardo de red
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));

    var result = List<EventEntity>.from(_events);

    if (category != null) {
      result = result.where((e) => e.category == category).toList();
    }

    if (searchQuery != null && searchQuery.trim().isNotEmpty) {
      final q = searchQuery.toLowerCase();
      result = result.where((e) =>
        e.title.toLowerCase().contains(q) ||
        e.description.toLowerCase().contains(q) ||
        e.venueName.toLowerCase().contains(q)
      ).toList();
    }

    return result;
  }

  @override
  Future<EventEntity> getEventById(String eventId) async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));
    final event = _events.firstWhere(
      (e) => e.id == eventId,
      orElse: () => throw EventNotFoundFailure('Evento no encontrado: $eventId'),
    );
    return event;
  }

  @override
  Future<EventEntity> toggleSubscription(String eventId, {String? email}) async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));
    final index = _events.indexWhere((e) => e.id == eventId);
    if (index == -1) throw EventNotFoundFailure('Evento no encontrado: $eventId');

    final current = _events[index];
    final updated = current.copyWith(isSubscribed: !current.isSubscribed);
    _events[index] = updated;
    return updated;
  }
}
