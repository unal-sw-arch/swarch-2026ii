import 'package:equatable/equatable.dart';

enum LoanStatus {
  returnedOnTime, // Devuelto a tiempo
  returnedLate,   // Devuelto con retraso
  active,         // En curso / En préstamo actual
  damaged,        // Con reporte de daño o pérdida (causa de RN-03)
}

/// Representa un registro en el historial de préstamos de implementos de un estudiante
class LoanRecordEntity extends Equatable {
  final String id;
  final String implementName;
  final String facultyName;
  final String pickupLocation;
  final DateTime borrowedAt;
  final DateTime? returnedAt;
  final LoanStatus status;
  final String? penaltyNote;

  const LoanRecordEntity({
    required this.id,
    required this.implementName,
    required this.facultyName,
    required this.pickupLocation,
    required this.borrowedAt,
    this.returnedAt,
    required this.status,
    this.penaltyNote,
  });

  @override
  List<Object?> get props => [
    id,
    implementName,
    facultyName,
    pickupLocation,
    borrowedAt,
    returnedAt,
    status,
    penaltyNote,
  ];
}
