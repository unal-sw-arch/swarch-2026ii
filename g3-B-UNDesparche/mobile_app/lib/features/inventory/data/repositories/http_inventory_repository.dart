import 'dart:async';
import 'package:dio/dio.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/api_endpoints.dart';
import '../../../../core/network/dio_client.dart';
import '../../../../core/utils/json_coerce.dart';
import '../../domain/entities/implement_entity.dart';
import '../../domain/entities/reservation_entity.dart';
import '../../domain/repositories/inventory_repository.dart';
import '../models/implement_model.dart';
import '../models/reservation_model.dart';

/// Implementacion remota de [InventoryRepository] para conectarse al API Gateway (:8000)
class HttpInventoryRepository implements InventoryRepository {
  final DioClient client;
  final StreamController<ReservationEntity?> _reservationStreamController =
      StreamController<ReservationEntity?>.broadcast();

  HttpInventoryRepository({required this.client});

  @override
  Future<List<ImplementEntity>> getImplements({
    String? facultyName,
    ImplementCategory? category,
  }) async {
    try {
      final response = await client.dio.get(
        ApiEndpoints.implements,
        queryParameters: {
          if (facultyName != null && facultyName != 'Todas')
            'faculty': ImplementModel.toBackendFaculty(facultyName),
          if (category != null)
            'category': ImplementModel.toBackendCategory(category),
        },
      );

      final List<dynamic> data =
          response.data is List ? response.data as List : ((response.data as Map)['results'] as List? ?? const []);
      return data.map((json) => ImplementModel.fromJson(json as Map<String, dynamic>)).toList();
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerInventoryFailure('Error al consultar inventario remoto: ${e.message}');
    }
  }

  @override
  Future<ImplementEntity> getImplementById(String implementId) async {
    try {
      final response = await client.dio.get(ApiEndpoints.implementDetail(implementId));
      return ImplementModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) {
        throw const ImplementNotFoundFailure();
      }
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerInventoryFailure('Error al consultar implemento: ${e.message}');
    }
  }

  @override
  Future<ReservationEntity> createReservation({
    required String implementId,
    required String userId,
    required String userEmail,
    required bool disclaimerAccepted,
  }) async {
    if (!disclaimerAccepted) {
      throw const DisclaimerNotAcceptedFailure();
    }

    try {
      final implementIntId = int.tryParse(implementId) ?? implementId;
      final response = await client.dio.post(
        ApiEndpoints.reserves,
        data: {
          'implement': implementIntId,
        },
      );

      final reservation = ReservationModel.fromJson(response.data as Map<String, dynamic>);
      _reservationStreamController.add(reservation);
      return reservation;
    } on DioException catch (e) {
      if (e.response?.statusCode == 409) {
        throw const SimultaneousReservationLimitFailure();
      }
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      final msg = asString(
          e.response?.data is Map ? (e.response!.data as Map)['detail'] : null,
          e.message ?? 'Error al procesar reserva');
      throw ServerInventoryFailure(msg);
    }
  }

  @override
  Future<void> cancelReservation(String reservationId) async {
    try {
      await client.dio.post(ApiEndpoints.reserveCancel(reservationId));
      _reservationStreamController.add(null);
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerInventoryFailure('Error al cancelar reserva: ${e.message}');
    }
  }

  @override
  Future<ReservationEntity?> getActiveReservation(String userId) async {
    try {
      final response = await client.dio.get(ApiEndpoints.reserves);
      final List<dynamic> data =
          response.data is List ? response.data as List : ((response.data as Map)['results'] as List? ?? const []);
      if (data.isEmpty) {
        return null;
      }
      final reservations = data
          .map((json) => ReservationModel.fromJson(json as Map<String, dynamic>))
          .toList();

      final active = reservations.cast<ReservationEntity?>().firstWhere(
        (r) => r != null && r.status == ReservationStatus.active && !r.isExpired,
        orElse: () => null,
      );
      return active;
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) {
        return null;
      }
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.connectionError) {
        throw const NetworkFailure();
      }
      throw ServerInventoryFailure('Error al consultar reserva activa: ${e.message}');
    }
  }

  @override
  Stream<ReservationEntity?> watchActiveReservation(String userId) {
    return _reservationStreamController.stream;
  }

  @override
  Future<void> dispose() async {
    if (!_reservationStreamController.isClosed) {
      await _reservationStreamController.close();
    }
  }
}
