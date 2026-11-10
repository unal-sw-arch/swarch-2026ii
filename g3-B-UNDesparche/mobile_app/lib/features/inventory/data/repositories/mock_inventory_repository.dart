import 'dart:async';
import '../../../../core/config/app_config.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/implement_entity.dart';
import '../../domain/entities/reservation_entity.dart';
import '../../domain/repositories/inventory_repository.dart';

/// Implementacion mockeada de [InventoryRepository] que valida las reglas de negocio
/// en memoria y emite actualizaciones reactivas del temporizador de 10 minutos (RN-02).
class MockInventoryRepository implements InventoryRepository {
  final StreamController<ReservationEntity?> _activeReservationController =
      StreamController<ReservationEntity?>.broadcast();

  ReservationEntity? _currentActiveReservation;

  final List<ImplementEntity> _implements = [
    // Facultad de Ingenieria
    const ImplementEntity(
      id: 'imp-001',
      name: 'Balon de Microfutbol Golty No. 4',
      category: ImplementCategory.balls,
      facultyName: 'Ingenieria',
      pickupLocation: 'Taquilla Canchas de Minas (Ed. 411)',
      status: ImplementStatus.available,
      usageTimeMinutes: 120,
      imageUrl: 'https://images.unsplash.com/photo-1575361204480-aadea25e6e68?auto=format&fit=crop&w=600&q=80',
      description: 'Balon de cuero sintetico reglamentario para piso duro. Presentar carnet fisico para retirar.',
    ),
    const ImplementEntity(
      id: 'imp-002',
      name: 'Set de Raquetas y Pelotas de Tenis de Mesa',
      category: ImplementCategory.rackets,
      facultyName: 'Ingenieria',
      pickupLocation: 'Taquilla Edificio CyT (Ed. 453)',
      status: ImplementStatus.available,
      usageTimeMinutes: 60,
      imageUrl: 'https://images.unsplash.com/photo-1609710228159-0fa9bd7c0827?auto=format&fit=crop&w=600&q=80',
      description: 'Par de raquetas Butterfly con esponja de 2mm y 3 pelotas de entrenamiento.',
    ),
    const ImplementEntity(
      id: 'imp-003',
      name: 'Reloj y Tablero de Ajedrez Profesional',
      category: ImplementCategory.boardGames,
      facultyName: 'Ingenieria',
      pickupLocation: 'Taquilla Canchas de Minas (Ed. 411)',
      status: ImplementStatus.reserved,
      usageTimeMinutes: 120,
      imageUrl: 'https://images.unsplash.com/photo-1586165368502-1bad197a6461?auto=format&fit=crop&w=600&q=80',
      description: 'Piezas plomadas tipo Staunton con tapete enrollable y reloj digital DGT 2010.',
    ),
    // Facultad de Ciencias Humanas
    const ImplementEntity(
      id: 'imp-004',
      name: 'Balon de Voleibol Mikasa V200W',
      category: ImplementCategory.balls,
      facultyName: 'Ciencias Humanas',
      pickupLocation: 'Taquilla Bienestar Humanas (Ed. 224 - Jardin Freud)',
      status: ImplementStatus.available,
      usageTimeMinutes: 90,
      imageUrl: 'https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=600&q=80',
      description: 'Balon oficial para cancha multiple o pasto en el jardin.',
    ),
    const ImplementEntity(
      id: 'imp-005',
      name: 'Jenga Gigante de Madera',
      category: ImplementCategory.boardGames,
      facultyName: 'Ciencias Humanas',
      pickupLocation: 'Taquilla Bienestar Humanas (Ed. 224 - Jardin Freud)',
      status: ImplementStatus.available,
      usageTimeMinutes: 60,
      imageUrl: 'https://images.unsplash.com/photo-1543007630-9710e4a00a20?auto=format&fit=crop&w=600&q=80',
      description: 'Bloques de madera maciza para juego al aire libre en el campus.',
    ),
    // Polideportivo Central
    const ImplementEntity(
      id: 'imp-006',
      name: 'Balon de Baloncesto Molten BG4500 #7',
      category: ImplementCategory.balls,
      facultyName: 'Bienestar Central',
      pickupLocation: 'Polideportivo Central (Ed. 412)',
      status: ImplementStatus.borrowed,
      usageTimeMinutes: 90,
      imageUrl: 'https://images.unsplash.com/photo-1519766304817-4f37bda74a29?auto=format&fit=crop&w=600&q=80',
      description: 'Cuero compuesto de alta adherencia para coliseo cubierto.',
    ),
  ];

  @override
  Future<List<ImplementEntity>> getImplements({
    String? facultyName,
    ImplementCategory? category,
  }) async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));

    var list = List<ImplementEntity>.from(_implements);
    if (facultyName != null && facultyName.isNotEmpty && facultyName != 'Todas') {
      list = list.where((i) => i.facultyName.toLowerCase() == facultyName.toLowerCase()).toList();
    }
    if (category != null) {
      list = list.where((i) => i.category == category).toList();
    }
    return list;
  }

  @override
  Future<ImplementEntity> getImplementById(String implementId) async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));
    return _implements.firstWhere(
      (i) => i.id == implementId,
      orElse: () => throw Exception('Implemento no encontrado: $implementId'),
    );
  }

  @override
  Future<ReservationEntity> createReservation({
    required String implementId,
    required String userId,
    required String userEmail,
    required bool disclaimerAccepted,
  }) async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));

    // Validacion RF-20 / RF-21: Descargo de responsabilidad
    if (!disclaimerAccepted) {
      throw const DisclaimerNotAcceptedFailure('Debe aceptar el descargo de responsabilidad antes de solicitar el implemento.');
    }

    // Validacion RN-01: Solo correos @unal.edu.co
    if (!userEmail.endsWith('@unal.edu.co')) {
      throw const InvalidEmailDomainFailure('Solo los miembros con correo institucional @unal.edu.co pueden reservar implementos.');
    }

    // Validacion RN-05: Maximo 1 implemento simultaneo
    if (_currentActiveReservation != null &&
        !_currentActiveReservation!.isExpired &&
        _currentActiveReservation!.status == ReservationStatus.active) {
      throw const SimultaneousReservationLimitFailure('Ya tienes una reserva activa en curso. No puedes tener mas de 1 implemento simultaneo.');
    }

    // Buscar el implemento
    final index = _implements.indexWhere((i) => i.id == implementId);
    if (index == -1) throw const ImplementNotFoundFailure('El implemento no existe.');

    final implement = _implements[index];
    if (implement.status != ImplementStatus.available) {
      throw const ImplementUnavailableFailure('El implemento no se encuentra disponible para reserva.');
    }

    // Crear reserva con ventana de 10 minutos (RN-02)
    final now = DateTime.now();
    final expires = now.add(const Duration(minutes: AppConfig.reservationTimeoutMinutes));

    final reservation = ReservationEntity(
      id: 'res-${DateTime.now().millisecondsSinceEpoch}',
      implementId: implement.id,
      implementName: implement.name,
      pickupLocation: implement.pickupLocation,
      userId: userId,
      userEmail: userEmail,
      reservedAt: now,
      expiresAt: expires,
      status: ReservationStatus.active,
      disclaimerAccepted: true,
    );

    // Actualizar estado del implemento a Reservado
    _implements[index] = implement.copyWith(status: ImplementStatus.reserved);
    _currentActiveReservation = reservation;
    _activeReservationController.add(_currentActiveReservation);

    return reservation;
  }

  @override
  Future<void> cancelReservation(String reservationId) async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));

    if (_currentActiveReservation != null && _currentActiveReservation!.id == reservationId) {
      // Liberar implemento
      final impIndex = _implements.indexWhere((i) => i.id == _currentActiveReservation!.implementId);
      if (impIndex != -1) {
        _implements[impIndex] = _implements[impIndex].copyWith(status: ImplementStatus.available);
      }

      _currentActiveReservation = _currentActiveReservation!.copyWith(status: ReservationStatus.cancelled);
      _activeReservationController.add(null);
    }
  }

  @override
  Future<ReservationEntity?> getActiveReservation(String userId) async {
    await Future.delayed(const Duration(milliseconds: 200));
    if (_currentActiveReservation == null) return null;

    if (_currentActiveReservation!.status != ReservationStatus.active) {
      return null;
    }

    if (_currentActiveReservation!.isExpired) {
      // Auto-expirar segun RN-02
      final impIndex = _implements.indexWhere((i) => i.id == _currentActiveReservation!.implementId);
      if (impIndex != -1) {
        _implements[impIndex] = _implements[impIndex].copyWith(status: ImplementStatus.available);
      }
      _currentActiveReservation = _currentActiveReservation!.copyWith(status: ReservationStatus.expired);
      _activeReservationController.add(null);
      return null;
    }
    return _currentActiveReservation;
  }

  @override
  Stream<ReservationEntity?> watchActiveReservation(String userId) {
    return _activeReservationController.stream;
  }

  @override
  Future<void> dispose() async {
    if (!_activeReservationController.isClosed) {
      await _activeReservationController.close();
    }
  }
}
