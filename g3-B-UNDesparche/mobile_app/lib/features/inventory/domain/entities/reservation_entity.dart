import 'package:equatable/equatable.dart';

enum ReservationStatus {
  active,    // En cuenta regresiva de 10 min
  claimed,   // Reclamado fisicamente en taquilla
  expired,   // No se reclamo en 10 min y se libero
  cancelled, // Cancelado por el estudiante
}

/// Representa la reserva temporal de un implemento segun la regla RN-02 (10 minutos)
class ReservationEntity extends Equatable {
  final String id;
  final String implementId;
  final String implementName;
  final String pickupLocation;
  final String userId;
  final String userEmail;
  final DateTime reservedAt;
  final DateTime expiresAt;
  final ReservationStatus status;
  final bool disclaimerAccepted;

  const ReservationEntity({
    required this.id,
    required this.implementId,
    required this.implementName,
    required this.pickupLocation,
    required this.userId,
    required this.userEmail,
    required this.reservedAt,
    required this.expiresAt,
    required this.status,
    required this.disclaimerAccepted,
  });

  /// Segundos restantes antes de que la reserva expire (regla RN-02)
  int get remainingSeconds {
    if (status != ReservationStatus.active) return 0;
    final diff = expiresAt.difference(DateTime.now()).inSeconds;
    return diff > 0 ? diff : 0;
  }

  /// Indica si la ventana de 10 minutos ya se agoto
  bool get isExpired => remainingSeconds <= 0 && status == ReservationStatus.active;

  ReservationEntity copyWith({
    String? id,
    String? implementId,
    String? implementName,
    String? pickupLocation,
    String? userId,
    String? userEmail,
    DateTime? reservedAt,
    DateTime? expiresAt,
    ReservationStatus? status,
    bool? disclaimerAccepted,
  }) {
    return ReservationEntity(
      id: id ?? this.id,
      implementId: implementId ?? this.implementId,
      implementName: implementName ?? this.implementName,
      pickupLocation: pickupLocation ?? this.pickupLocation,
      userId: userId ?? this.userId,
      userEmail: userEmail ?? this.userEmail,
      reservedAt: reservedAt ?? this.reservedAt,
      expiresAt: expiresAt ?? this.expiresAt,
      status: status ?? this.status,
      disclaimerAccepted: disclaimerAccepted ?? this.disclaimerAccepted,
    );
  }

  @override
  List<Object?> get props => [
    id,
    implementId,
    implementName,
    pickupLocation,
    userId,
    userEmail,
    reservedAt,
    expiresAt,
    status,
    disclaimerAccepted,
  ];
}
