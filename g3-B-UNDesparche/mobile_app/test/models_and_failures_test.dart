import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/error/failures.dart';
import 'package:mobile_app/features/auth/data/models/user_model.dart';
import 'package:mobile_app/features/auth/domain/entities/user_entity.dart';
import 'package:mobile_app/features/events/data/models/event_model.dart';
import 'package:mobile_app/features/events/domain/entities/event_entity.dart';
import 'package:mobile_app/features/inventory/data/models/implement_model.dart';
import 'package:mobile_app/features/inventory/data/models/reservation_model.dart';
import 'package:mobile_app/features/inventory/domain/entities/implement_entity.dart';
import 'package:mobile_app/features/inventory/domain/entities/reservation_entity.dart';

void main() {
  group('Pruebas de Jerarquía de Fallos Tipados (Clean Architecture - Failures)', () {
    test('InvalidEmailDomainFailure retorna código y mensaje correcto', () {
      const failure = InvalidEmailDomainFailure();
      expect(failure.code, equals('INVALID_DOMAIN'));
      expect(failure.message, contains('@unal.edu.co'));
      expect(failure, isA<Exception>());
    });

    test('SimultaneousReservationLimitFailure verifica RN-05', () {
      const failure = SimultaneousReservationLimitFailure();
      expect(failure.code, equals('ACTIVE_RESERVATION_LIMIT'));
      expect(failure.message, contains('RN-05'));
    });

    test('DisclaimerNotAcceptedFailure verifica RF-20 / RF-21', () {
      const failure = DisclaimerNotAcceptedFailure();
      expect(failure.code, equals('DISCLAIMER_REQUIRED'));
      expect(failure.message, contains('descargo de responsabilidad'));
    });

    test('Comparación por valor en Failures con Equatable', () {
      const failure1 = ServerAuthFailure('Credenciales inválidas');
      const failure2 = ServerAuthFailure('Credenciales inválidas');
      expect(failure1, equals(failure2));
    });
  });

  group('Pruebas de Modelos DTO y Serialización JSON (Data Layer)', () {
    test('UserModel.fromJson mapea roles y atributos correctamente', () {
      final json = {
        'id': 'usr-100',
        'full_name': 'Camilo Gomez',
        'email': 'cgomez@unal.edu.co',
        'faculty': 'Ingenieria',
        'role': 'communityMember',
        'status': 'active',
        'avatar_url': 'https://example.com/photo.jpg',
      };

      final model = UserModel.fromJson(json);

      expect(model.id, equals('usr-100'));
      expect(model.fullName, equals('Camilo Gomez'));
      expect(model.email, equals('cgomez@unal.edu.co'));
      expect(model.role, equals(UserRole.communityMember));
      expect(model.status, equals(UserStatus.active));
      expect(model.canBorrowImplements, isTrue);

      final backToJson = model.toJson();
      expect(backToJson['email'], equals('cgomez@unal.edu.co'));
      expect(backToJson['role'], equals('communityMember'));
    });

    test('EventModel.fromJson mapea categorías y fechas correctamente', () {
      final now = DateTime.now();
      final json = {
        'id': 'ev-200',
        'title': 'Cine Foro León de Greiff',
        'description': 'Cine arte latinoamericano',
        'category': 'cultura',
        'place': 'Auditorio León de Greiff',
        'latitude': 4.6366,
        'longitude': -74.0827,
        'start_time': now.toIso8601String(),
        'end_time': now.add(const Duration(hours: 3)).toIso8601String(),
        'organizer': 'Divulgación Cultural',
        'status': 'scheduled',
        'is_subscribed': true,
      };

      final model = EventModel.fromJson(json);

      expect(model.id, equals('ev-200'));
      expect(model.title, equals('Cine Foro León de Greiff'));
      expect(model.category, equals(EventCategory.culture));
      expect(model.venueName, equals('Auditorio León de Greiff'));
      expect(model.latitude, equals(4.6366));
      expect(model.longitude, equals(-74.0827));
      expect(model.isSubscribed, isTrue);
    });

    test('ImplementModel y ReservationModel serializan y deserializan correctamente', () {
      final implementJson = {
        'id': 'imp-001',
        'name': 'Balón de Microfútbol Golty',
        'category': 'ball',
        'faculty': 'Ingenieria',
        'location': 'Taquilla Minas',
        'status': 'available',
        'usage_time_minutes': 120,
        'description': 'Balón No. 4',
      };

      final implement = ImplementModel.fromJson(implementJson);
      expect(implement.name, equals('Balón de Microfútbol Golty'));
      expect(implement.category, equals(ImplementCategory.balls));
      expect(implement.status, equals(ImplementStatus.available));

      final reservationJson = {
        'id': 'res-001',
        'implement_id': implement.id,
        'implement_name': implement.name,
        'pickup_location': implement.pickupLocation,
        'user_id': 'usr-1',
        'user_email': 'cgomez@unal.edu.co',
        'reserved_at': DateTime.now().toIso8601String(),
        'expires_at': DateTime.now().add(const Duration(minutes: 10)).toIso8601String(),
        'status': 'active',
        'disclaimer_accepted': true,
      };

      final reservation = ReservationModel.fromJson(reservationJson);
      expect(reservation.id, equals('res-001'));
      expect(reservation.status, equals(ReservationStatus.active));
      expect(reservation.remainingSeconds, inInclusiveRange(590, 600));
    });
  });
}
