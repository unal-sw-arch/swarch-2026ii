import '../../../../core/utils/json_coerce.dart';
import '../../domain/entities/reservation_entity.dart';

/// Modelo de datos (DTO) para la serialización y deserialización de reservas de implementos
class ReservationModel extends ReservationEntity {
  const ReservationModel({
    required super.id,
    required super.implementId,
    required super.implementName,
    required super.pickupLocation,
    required super.userId,
    required super.userEmail,
    required super.reservedAt,
    required super.expiresAt,
    required super.status,
    required super.disclaimerAccepted,
  });

  factory ReservationModel.fromJson(Map<String, dynamic> json) {
    final now = DateTime.now();

    String userIdStr = '';
    String userEmailStr = '';
    if (json['user'] is Map) {
      final userMap = json['user'] as Map;
      userIdStr = asString(userMap['id'] ?? userMap['firebase_uid']);
      userEmailStr = asString(userMap['email']);
    } else {
      userIdStr = asString(json['user_id'] ?? json['user']);
      userEmailStr = asString(json['user_email']);
    }

    return ReservationModel(
      id: asString(json['id']),
      implementId: asString(json['implement_id'] ?? json['implement']),
      implementName: asString(json['implement_name'], 'Implemento'),
      pickupLocation: asString(json['pickup_location'] ?? json['location'], 'Taquilla Bienestar'),
      userId: userIdStr,
      userEmail: userEmailStr,
      reservedAt: DateTime.tryParse(asString(json['datetime_reserved'] ?? json['reserved_at'])) ?? now,
      expiresAt: DateTime.tryParse(asString(json['datetime_expiration'] ?? json['expires_at'])) ?? now.add(const Duration(minutes: 10)),
      status: _parseStatus(json),
      disclaimerAccepted: asBool(json['disclaimer_accepted'], true),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'implement': implementId,
      'implement_id': implementId,
      'implement_name': implementName,
      'pickup_location': pickupLocation,
      'user_id': userId,
      'user_email': userEmail,
      'datetime_reserved': reservedAt.toIso8601String(),
      'datetime_expiration': expiresAt.toIso8601String(),
      'active': status == ReservationStatus.active,
      'status': status.name,
      'disclaimer_accepted': disclaimerAccepted,
    };
  }

  static ReservationStatus _parseStatus(Map<String, dynamic> json) {
    if (json.containsKey('active')) {
      final active = asBool(json['active']);
      if (!active) {
        final state = asString(json['implement_state']).toUpperCase();
        if (state == 'PRE') return ReservationStatus.claimed;
        return ReservationStatus.expired;
      }
      return ReservationStatus.active;
    }
    final str = json['status']?.toString().toLowerCase() ?? '';
    if (str.contains('claim') || str.contains('entrega')) return ReservationStatus.claimed;
    if (str.contains('expir')) return ReservationStatus.expired;
    if (str.contains('cancel')) return ReservationStatus.cancelled;
    return ReservationStatus.active;
  }
}
