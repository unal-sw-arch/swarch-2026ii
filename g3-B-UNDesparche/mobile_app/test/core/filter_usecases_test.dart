import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/features/events/domain/entities/event_entity.dart';
import 'package:mobile_app/features/events/domain/usecases/filter_events.dart';
import 'package:mobile_app/features/inventory/domain/entities/implement_entity.dart';
import 'package:mobile_app/features/inventory/domain/usecases/filter_implements.dart';

void main() {
  group('EventFilter (#7, regla única compartida)', () {
    final base = DateTime(2026, 1, 1, 10);
    final events = [
      EventEntity(
        id: 'e1',
        title: 'Torneo de Fútbol',
        description: 'Canchas de Ingeniería',
        category: EventCategory.sports,
        venueName: 'Canchas Minas',
        latitude: 0,
        longitude: 0,
        startDateTime: base.add(const Duration(hours: 3)),
        endDateTime: base.add(const Duration(hours: 5)),
        organizer: 'Bienestar',
        status: EventStatus.scheduled,
      ),
      EventEntity(
        id: 'e2',
        title: 'Concierto de Jazz',
        description: 'Auditorio León de Greiff',
        category: EventCategory.culture,
        venueName: 'Auditorio',
        latitude: 0,
        longitude: 0,
        startDateTime: base,
        endDateTime: base.add(const Duration(hours: 2)),
        organizer: 'Cultura',
        status: EventStatus.scheduled,
      ),
    ];

    test('sin filtro devuelve todos, ordenados por fecha (RF-6)', () {
      final result = const EventFilter().apply(events);
      expect(result.length, 2);
      expect(result.first.id, 'e2'); // empieza antes
    });

    test('filtra por categoría', () {
      final result = const EventFilter(category: EventCategory.sports).apply(events);
      expect(result.map((e) => e.id), ['e1']);
    });

    test('busca en título, descripción y lugar', () {
      expect(const EventFilter(query: 'jazz').count(events), 1);
      expect(const EventFilter(query: 'ingeniería').count(events), 1);
      expect(const EventFilter(query: 'auditorio').count(events), 1);
      expect(const EventFilter(query: 'xyz').count(events), 0);
    });

    test('count y apply coinciden', () {
      const f = EventFilter(category: EventCategory.culture);
      expect(f.count(events), f.apply(events).length);
    });
  });

  group('ImplementFilter (#7, regla única compartida)', () {
    const items = [
      ImplementEntity(
        id: 'i1',
        name: 'Balón de Fútbol',
        category: ImplementCategory.balls,
        facultyName: 'Ingenieria',
        pickupLocation: 'Taquilla Ed. 411',
        status: ImplementStatus.available,
        usageTimeMinutes: 120,
        description: 'Balón reglamentario',
      ),
      ImplementEntity(
        id: 'i2',
        name: 'Jenga Gigante',
        category: ImplementCategory.boardGames,
        facultyName: 'Ciencias Humanas',
        pickupLocation: 'Taquilla Ed. 224',
        status: ImplementStatus.available,
        usageTimeMinutes: 60,
        description: 'Juego de mesa',
      ),
    ];

    test('faculty "Todas" devuelve todos', () {
      expect(const ImplementFilter().apply(items).length, 2);
    });

    test('filtra por facultad', () {
      final r = const ImplementFilter(faculty: 'Ingenieria').apply(items);
      expect(r.map((i) => i.id), ['i1']);
    });

    test('filtra por categoría', () {
      final r =
          const ImplementFilter(category: ImplementCategory.boardGames).apply(items);
      expect(r.map((i) => i.id), ['i2']);
    });

    test('busca por nombre, lugar y descripción', () {
      expect(const ImplementFilter(query: 'balón').count(items), 1);
      expect(const ImplementFilter(query: '224').count(items), 1);
      expect(const ImplementFilter(query: 'mesa').count(items), 1);
      expect(const ImplementFilter(query: 'raqueta').count(items), 0);
    });

    test('count y apply coinciden', () {
      const f = ImplementFilter(faculty: 'Ciencias Humanas');
      expect(f.count(items), f.apply(items).length);
    });
  });
}
