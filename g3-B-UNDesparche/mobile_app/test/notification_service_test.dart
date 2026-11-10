import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/services/notification_service.dart';

void main() {
  late NotificationService notificationService;

  setUp(() {
    notificationService = NotificationService();
  });

  tearDown(() {
    notificationService.dispose();
  });

  group('Pruebas de NotificationService (Fase 5: Regla RN-02 y Alertas)', () {
    test('notifyReservationExpiringSoon emite notificación tipada con payload de 3 minutos', () async {
      unawaited(expectLater(
        notificationService.onTypedNotification,
        emits(predicate<AppNotification>((n) =>
            n.type == NotificationType.reservationExpiring &&
            n.body.contains('3 minutos') &&
            n.payload?['implementName'] == 'Balón de Fútbol Sala')),
      ));

      notificationService.notifyReservationExpiringSoon(
        implementName: 'Balón de Fútbol Sala',
        pickupLocation: 'Taquilla Minas (Ed. 411)',
        secondsLeft: 180, // 3 minutos exactos
      );
    });

    testWidgets('notifyReservationExpiringSoon muestra SnackBar flotante en context', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () {
                  notificationService.notifyReservationExpiringSoon(
                    context: context,
                    implementName: 'Ajedrez Profesional',
                    pickupLocation: 'Taquilla CyT (Ed. 453)',
                    secondsLeft: 180,
                  );
                },
                child: const Text('Disparar Alerta'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Disparar Alerta'));
      await tester.pump(); // Iniciar animación de SnackBar

      expect(find.byType(SnackBar), findsOneWidget);
      expect(find.textContaining('3 minutos para reclamar "Ajedrez Profesional"'), findsOneWidget);
      expect(find.byIcon(Icons.alarm), findsOneWidget);
    });

    testWidgets('notifyEventChange emite novedad y muestra SnackBar de evento', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () {
                  notificationService.notifyEventChange(
                    context: context,
                    eventTitle: 'Torneo Microfútbol',
                    changeDescription: 'Cambio de hora a las 2:00 PM',
                  );
                },
                child: const Text('Notificar Evento'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Notificar Evento'));
      await tester.pump();

      expect(find.byType(SnackBar), findsOneWidget);
      expect(find.textContaining('Novedad en "Torneo Microfútbol": Cambio de hora'), findsOneWidget);
    });
  });
}
