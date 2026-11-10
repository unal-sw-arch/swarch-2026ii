import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/network/dio_client.dart';
import 'package:mobile_app/features/events/data/repositories/http_events_repository.dart';
import 'package:mobile_app/features/inventory/data/repositories/http_inventory_repository.dart';

void main() {
  group('Pruebas de Integración Móvil contra el API Gateway Real (:8000)', () {
    late DioClient dioClient;
    late HttpEventsRepository eventsRepo;
    late HttpInventoryRepository inventoryRepo;

    setUp(() {
      dioClient = DioClient();
      // En pruebas locales desde el host, el Gateway está en 127.0.0.1:8000
      dioClient.dio.options.baseUrl = 'http://127.0.0.1:8000';

      eventsRepo = HttpEventsRepository(client: dioClient);
      inventoryRepo = HttpInventoryRepository(client: dioClient);
    });

    test('Obtener catálogo de eventos en vivo desde el Gateway', () async {
      final events = await eventsRepo.getEvents();

      expect(events, isNotEmpty);
      expect(events.length, greaterThanOrEqualTo(3));

      final firstEvent = events.first;
      expect(firstEvent.title, isNotEmpty);
      expect(firstEvent.venueName, isNotEmpty);
      expect(firstEvent.startDateTime, isNotNull);
    });

    test('Obtener detalle de un evento por ID desde el Gateway', () async {
      final events = await eventsRepo.getEvents();
      final firstId = events.first.id;

      final detail = await eventsRepo.getEventById(firstId);
      expect(detail.id, equals(firstId));
      expect(detail.title, equals(events.first.title));
    });

    test('Obtener catálogo de implementos en vivo desde el Gateway', () async {
      final implements = await inventoryRepo.getImplements();

      expect(implements, isNotEmpty);
      expect(implements.length, greaterThanOrEqualTo(5));

      final firstItem = implements.first;
      expect(firstItem.name, isNotEmpty);
      expect(firstItem.description, isNotEmpty);
    });

    test('Obtener detalle de un implemento por ID desde el Gateway', () async {
      final implements = await inventoryRepo.getImplements();
      final firstId = implements.first.id;

      final detail = await inventoryRepo.getImplementById(firstId);
      expect(detail.id, equals(firstId));
      expect(detail.name, equals(implements.first.name));
    });

    test('Inyectar token demo y consultar perfil /users/me/ a través del Gateway', () async {
      dioClient.authInterceptor.setToken('demo_user_dvelasquezg@unal.edu.co');

      final response = await dioClient.dio.get('/users/me/');

      expect(response.statusCode, equals(200));
      expect(response.data['email'], equals('dvelasquezg@unal.edu.co'));
      expect(response.data['role'], equals('COMMUNITY'));
      expect(response.data['status'], equals('ACT'));
    });
  });
}
