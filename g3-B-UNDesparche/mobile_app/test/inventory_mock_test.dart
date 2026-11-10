import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/features/inventory/data/repositories/mock_inventory_repository.dart';
import 'package:mobile_app/features/inventory/domain/entities/implement_entity.dart';
import 'package:mobile_app/features/inventory/domain/entities/reservation_entity.dart';

void main() {
  late MockInventoryRepository repository;

  setUp(() {
    repository = MockInventoryRepository();
  });

  group('Pruebas de Reglas de Negocio de Inventario (UNDesparche)', () {
    test('RF-20 / RF-21: Debe fallar si NO se acepta el descargo de responsabilidad', () async {
      expect(
        () => repository.createReservation(
          implementId: 'imp-001',
          userId: 'usr-123',
          userEmail: 'estudiante@unal.edu.co',
          disclaimerAccepted: false, // Descargo NO aceptado
        ),
        throwsA(predicate((e) =>
            e is Exception &&
            e.toString().contains('Debe aceptar el descargo de responsabilidad'))),
      );
    });

    test('RN-01: Debe rechazar reservas si el correo NO termina en @unal.edu.co', () async {
      expect(
        () => repository.createReservation(
          implementId: 'imp-001',
          userId: 'usr-visitante',
          userEmail: 'visitante@gmail.com', // Correo externo
          disclaimerAccepted: true,
        ),
        throwsA(predicate((e) =>
            e is Exception &&
            e.toString().contains('Solo los miembros con correo institucional @unal.edu.co'))),
      );
    });

    test('RN-02: Al reservar con exito, debe crear la reserva con ventana de 10 minutos', () async {
      final reservation = await repository.createReservation(
        implementId: 'imp-001',
        userId: 'usr-student-001',
        userEmail: 'estudiante@unal.edu.co',
        disclaimerAccepted: true,
      );

      expect(reservation.status, equals(ReservationStatus.active));
      expect(reservation.remainingSeconds, inInclusiveRange(590, 600)); // Cerca de 600s (10 min)
      expect(reservation.isExpired, isFalse);
    });

    test('RN-05: Debe bloquear si el estudiante ya tiene una reserva activa en curso', () async {
      // 1. Primera reserva valida
      await repository.createReservation(
        implementId: 'imp-001',
        userId: 'usr-student-001',
        userEmail: 'estudiante@unal.edu.co',
        disclaimerAccepted: true,
      );

      // 2. Intento de segunda reserva simultanea
      expect(
        () => repository.createReservation(
          implementId: 'imp-002',
          userId: 'usr-student-001',
          userEmail: 'estudiante@unal.edu.co',
          disclaimerAccepted: true,
        ),
        throwsA(predicate((e) =>
            e is Exception &&
            e.toString().contains('Ya tienes una reserva activa'))),
      );
    });

    test('Cancelacion: Liberar el implemento al cancelar la reserva', () async {
      final reservation = await repository.createReservation(
        implementId: 'imp-001',
        userId: 'usr-student-001',
        userEmail: 'estudiante@unal.edu.co',
        disclaimerAccepted: true,
      );

      await repository.cancelReservation(reservation.id);

      final active = await repository.getActiveReservation('usr-student-001');
      expect(active, isNull);

      final implement = await repository.getImplementById('imp-001');
      expect(implement.status, equals(ImplementStatus.available));
    });
  });
}
