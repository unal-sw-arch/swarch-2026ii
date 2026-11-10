import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/theme/app_theme.dart';
import 'package:mobile_app/features/auth/domain/entities/user_entity.dart';
import 'package:mobile_app/features/events/domain/entities/event_entity.dart';
import 'package:mobile_app/features/events/presentation/widgets/event_card.dart';
import 'package:mobile_app/features/inventory/domain/entities/implement_entity.dart';
import 'package:mobile_app/features/inventory/domain/entities/reservation_entity.dart';
import 'package:mobile_app/features/inventory/presentation/widgets/active_reservation_banner.dart';
import 'package:mobile_app/features/inventory/presentation/widgets/implement_card.dart';

/// Pantalla angosta (320 px) con letra al 130 %: si algo se desborda, el test falla.
Widget _narrow(Widget child, {Brightness brightness = Brightness.light}) {
  return MaterialApp(
    theme: brightness == Brightness.dark ? AppTheme.darkTheme : AppTheme.lightTheme,
    home: MediaQuery(
      data: const MediaQueryData(size: Size(320, 640), textScaler: TextScaler.linear(1.3)),
      child: Scaffold(body: SingleChildScrollView(padding: const EdgeInsets.all(16), child: child)),
    ),
  );
}

final _event = EventEntity(
  id: 'e1',
  title: 'Torneo de Microfútbol Interfacultades de Ingeniería',
  description: 'Descripción',
  category: EventCategory.sports,
  venueName: 'Canchas de Minas (Ed. 411)',
  latitude: 4.6,
  longitude: -74.0,
  startDateTime: DateTime(2026, 11, 5, 15),
  endDateTime: DateTime(2026, 11, 5, 18),
  organizer: 'Bienestar',
  status: EventStatus.inProgress,
);

const _student = UserEntity(
  id: 'u1',
  fullName: 'Camilo Gomez',
  email: 'cgomez@unal.edu.co',
  role: UserRole.communityMember,
  status: UserStatus.active,
);

const _implement = ImplementEntity(
  id: 'i1',
  name: 'Set de Raquetas y Pelotas de Tenis de Mesa',
  category: ImplementCategory.rackets,
  facultyName: 'Ciencias Humanas',
  pickupLocation: 'Taquilla Bienestar Humanas (Ed. 224)',
  status: ImplementStatus.available,
  usageTimeMinutes: 60,
  description: 'Incluye 2 raquetas y 3 pelotas.',
);

void main() {
  setUp(() async {
    await initializeDateFormatting('es', null);
  });

  for (final brightness in Brightness.values) {
    group('sin desbordes en ${brightness.name}, 320 px y letra 130 %', () {
      testWidgets('EventCard', (tester) async {
        await tester.pumpWidget(_narrow(
          EventCard(event: _event, onTap: () {}, onToggleSubscription: () {}),
          brightness: brightness,
        ));
        await tester.pump(const Duration(milliseconds: 500));
        expect(tester.takeException(), isNull);
        expect(find.text('Deportes'), findsOneWidget);
        expect(find.text('ED. 411 · MINAS'), findsOneWidget);
      });

      testWidgets('ImplementCard disponible, visitante y sancionado', (tester) async {
        for (final user in [
          _student,
          const UserEntity(id: 'g', fullName: 'Visitante', email: 'v@x.com', role: UserRole.guest, status: UserStatus.active),
          const UserEntity(id: 's', fullName: 'Victor', email: 'v@unal.edu.co', role: UserRole.communityMember, status: UserStatus.sanctioned),
        ]) {
          await tester.pumpWidget(_narrow(
            ImplementCard(implement: _implement, currentUser: user, onReserve: () {}),
            brightness: brightness,
          ));
          await tester.pump(const Duration(milliseconds: 500));
          expect(tester.takeException(), isNull);
        }
      });

      testWidgets('ActiveReservationBanner muestra el reloj', (tester) async {
        final reservation = ReservationEntity(
          id: 'res-abcdef123456',
          implementId: 'i1',
          implementName: 'Set de Raquetas y Pelotas de Tenis de Mesa',
          pickupLocation: 'Taquilla Bienestar Humanas (Ed. 224)',
          userId: 'u1',
          userEmail: 'cgomez@unal.edu.co',
          reservedAt: DateTime.now(),
          expiresAt: DateTime.now().add(const Duration(minutes: 10)),
          status: ReservationStatus.active,
          disclaimerAccepted: true,
        );
        await tester.pumpWidget(_narrow(
          ActiveReservationBanner(reservation: reservation, remainingSeconds: 125, onCancel: () {}),
          brightness: brightness,
        ));
        await tester.pump(const Duration(milliseconds: 500));
        expect(tester.takeException(), isNull);
        expect(find.text('02:05'), findsOneWidget);
      });
    });
  }
}
