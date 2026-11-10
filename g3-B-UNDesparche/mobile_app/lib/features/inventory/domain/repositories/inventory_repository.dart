import '../entities/implement_entity.dart';
import '../entities/reservation_entity.dart';

/// Contrato abstracto para el modulo de inventario y reservas
abstract class InventoryRepository {
  /// Obtiene los implementos disponibles, filtrados opcionalmente por facultad y categoria
  Future<List<ImplementEntity>> getImplements({
    String? facultyName,
    ImplementCategory? category,
  });

  /// Detalle de un implemento
  Future<ImplementEntity> getImplementById(String implementId);

  /// Realiza la reserva temporal de 10 minutos (Reglas RN-02, RN-05, RF-20, RF-21)
  Future<ReservationEntity> createReservation({
    required String implementId,
    required String userId,
    required String userEmail,
    required bool disclaimerAccepted,
  });

  /// Cancela la reserva activa liberando el implemento
  Future<void> cancelReservation(String reservationId);

  /// Consulta la reserva activa del usuario (si tiene una)
  Future<ReservationEntity?> getActiveReservation(String userId);

  /// Stream reactivo para escuchar cambios en la reserva activa (para temporizadores en vivo)
  Stream<ReservationEntity?> watchActiveReservation(String userId);

  /// Libera recursos del repositorio (cierra el stream de reservas). Se llama al
  /// cerrar sesión para no dejar el StreamController abierto (#11).
  Future<void> dispose();
}
