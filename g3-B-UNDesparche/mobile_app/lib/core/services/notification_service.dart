import 'dart:async';
import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

enum NotificationType {
  reservationExpiring,
  eventChange,
}

/// Representa una notificación tipada del sistema UNDesparche
class AppNotification {
  final String id;
  final String title;
  final String body;
  final NotificationType type;
  final DateTime timestamp;
  final Map<String, dynamic>? payload;

  AppNotification({
    required this.id,
    required this.title,
    required this.body,
    required this.type,
    DateTime? timestamp,
    this.payload,
  }) : timestamp = timestamp ?? DateTime.now();
}

/// Servicio de Notificaciones y Alertas para el ciclo de vida de reservas y eventos
/// Soporta emisión reactiva en Stream, integración con SnackBar flotante y adaptador
/// preparado para plugins de notificaciones del sistema operativo (RN-02 y RF-13).
class NotificationService {
  final StreamController<String> _notificationStreamController =
      StreamController<String>.broadcast();
  final StreamController<AppNotification> _typedNotificationController =
      StreamController<AppNotification>.broadcast();

  Stream<String> get onNotification => _notificationStreamController.stream;
  Stream<AppNotification> get onTypedNotification => _typedNotificationController.stream;

  /// Alerta preventiva cuando restan 3 minutos para reclamar el implemento (RN-02)
  void notifyReservationExpiringSoon({
    BuildContext? context,
    required String implementName,
    required String pickupLocation,
    required int secondsLeft,
  }) {
    final minutes = secondsLeft ~/ 60;
    final message = '⚠️ ¡Atención! Te quedan solo $minutes minutos para reclamar "$implementName" en $pickupLocation antes de que sea liberado.';

    final notification = AppNotification(
      id: 'notif-res-${DateTime.now().millisecondsSinceEpoch}',
      title: '¡Reserva por expirar!',
      body: message,
      type: NotificationType.reservationExpiring,
      payload: {
        'implementName': implementName,
        'pickupLocation': pickupLocation,
        'secondsLeft': secondsLeft,
      },
    );

    _notificationStreamController.add(message);
    _typedNotificationController.add(notification);

    if (context != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              const Icon(Icons.alarm, color: Colors.white),
              const SizedBox(width: 12),
              Expanded(child: Text(message)),
            ],
          ),
          backgroundColor: AppColors.danger,
          duration: const Duration(seconds: 5),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  /// Alerta cuando se publica o cancela un evento suscrito (RF-13)
  void notifyEventChange({
    BuildContext? context,
    required String eventTitle,
    required String changeDescription,
  }) {
    final message = '🔔 Novedad en "$eventTitle": $changeDescription';

    final notification = AppNotification(
      id: 'notif-ev-${DateTime.now().millisecondsSinceEpoch}',
      title: 'Novedad en evento',
      body: message,
      type: NotificationType.eventChange,
      payload: {
        'eventTitle': eventTitle,
        'changeDescription': changeDescription,
      },
    );

    _notificationStreamController.add(message);
    _typedNotificationController.add(notification);

    if (context != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: AppColors.mintPrimary,
          duration: const Duration(seconds: 4),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  void dispose() {
    _notificationStreamController.close();
    _typedNotificationController.close();
  }
}
