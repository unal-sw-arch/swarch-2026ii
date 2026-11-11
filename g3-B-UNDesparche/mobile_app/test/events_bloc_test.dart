import 'package:flutter_test/flutter_test.dart';
import 'package:bloc_test/bloc_test.dart';
import 'package:mobile_app/features/events/domain/entities/event_entity.dart';
import 'package:mobile_app/features/events/domain/repositories/events_repository.dart';
import 'package:mobile_app/features/events/presentation/bloc/events_bloc.dart';
import 'package:mobile_app/features/events/presentation/bloc/events_event.dart';
import 'package:mobile_app/features/events/presentation/bloc/events_state.dart';

class _ImmediateEventsRepository implements EventsRepository {
  List<EventEntity> events = [
    EventEntity(
      id: 'ev-001',
      title: 'Torneo de Microfutbol',
      description: 'Canchas Minas',
      category: EventCategory.sports,
      venueName: 'Ed. 411 Canchas Minas',
      latitude: 4.6391,
      longitude: -74.0858,
      startDateTime: DateTime(2026, 10, 25, 10),
      endDateTime: DateTime(2026, 10, 25, 14),
      organizer: 'Bienestar Ingenieria',
      status: EventStatus.scheduled,
      isSubscribed: false,
    ),
    EventEntity(
      id: 'ev-002',
      title: 'Cine Club La Perla',
      description: 'Proyeccion de cine arte',
      category: EventCategory.culture,
      venueName: 'Ed. 104 Auditorio Leon de Greiff',
      latitude: 4.6366,
      longitude: -74.0827,
      startDateTime: DateTime(2026, 10, 26, 15),
      endDateTime: DateTime(2026, 10, 26, 18),
      organizer: 'Divulgacion Cultural',
      status: EventStatus.scheduled,
      isSubscribed: false,
    ),
  ];

  @override
  Future<List<EventEntity>> getEvents({EventCategory? category, String? searchQuery}) async {
    return List.from(events);
  }

  @override
  Future<EventEntity> getEventById(String eventId) async {
    return events.firstWhere((e) => e.id == eventId);
  }

  @override
  Future<EventEntity> toggleSubscription(String eventId, {String? email}) async {
    final idx = events.indexWhere((e) => e.id == eventId);
    final updated = events[idx].copyWith(isSubscribed: !events[idx].isSubscribed);
    events[idx] = updated;
    return updated;
  }
}

void main() {
  late _ImmediateEventsRepository repository;
  late EventsBloc eventsBloc;

  setUp(() {
    repository = _ImmediateEventsRepository();
    eventsBloc = EventsBloc(eventsRepository: repository);
  });

  tearDown(() {
    eventsBloc.close();
  });

  group('Pruebas de EventsBloc (Fase 3: Eventos y Mapa UNAL)', () {
    test('Estado inicial es EventsInitial', () {
      expect(eventsBloc.state, equals(const EventsInitial()));
    });

    blocTest<EventsBloc, EventsState>(
      'Carga todos los eventos ordenados por fecha al disparar LoadEventsRequested (RF-6)',
      build: () => eventsBloc,
      act: (bloc) => bloc.add(const LoadEventsRequested()),
      expect: () => [
        const EventsLoading(),
        isA<EventsLoaded>().having((s) => s.allEvents.length, 'tiene 2 eventos', 2),
      ],
      verify: (bloc) {
        final state = bloc.state as EventsLoaded;
        for (int i = 0; i < state.filteredEvents.length - 1; i++) {
          expect(
            state.filteredEvents[i].startDateTime.isBefore(state.filteredEvents[i + 1].startDateTime) ||
                state.filteredEvents[i].startDateTime.isAtSameMomentAs(state.filteredEvents[i + 1].startDateTime),
            isTrue,
          );
        }
      },
    );

    blocTest<EventsBloc, EventsState>(
      'Filtra correctamente por categoria Deportes (RF-5)',
      build: () => eventsBloc,
      act: (bloc) async {
        bloc.add(const LoadEventsRequested());
        bloc.add(const FilterEventsCategoryChanged(EventCategory.sports));
      },
      skip: 2, // Omitir loading e inicial
      expect: () => [
        isA<EventsLoaded>().having((s) => s.filteredEvents.length, 'solo deportes', 1),
      ],
    );

    blocTest<EventsBloc, EventsState>(
      'Alterna suscripcion de un evento para visitante con email (RF-12)',
      build: () => eventsBloc,
      act: (bloc) async {
        bloc.add(const LoadEventsRequested());
        bloc.add(const ToggleEventSubscriptionRequested(
          eventId: 'ev-001',
          visitorEmail: 'visitante@externo.com',
        ));
      },
      skip: 2, // Omitir loading e inicial
      expect: () => [
        isA<EventsLoaded>().having(
          (s) => s.allEvents.firstWhere((e) => e.id == 'ev-001').isSubscribed,
          'evento suscrito',
          isTrue,
        ),
      ],
    );
  });
}
