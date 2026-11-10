import '../../../auth/domain/entities/user_entity.dart';
import '../../domain/entities/loan_record_entity.dart';

/// Historial de préstamos de PRUEBA (RF-22). Vive fuera de la pantalla para no
/// inflarla (#13). Cuando exista el backend (users_service / inventory_service),
/// se reemplaza por `InventoryRepository.getLoanHistory()`.
abstract class MockLoanHistory {
  static List<LoanRecordEntity> forUser(UserEntity user) {
    if (user.isGuest) return const [];

    if (user.isSanctioned) {
      return [
        LoanRecordEntity(
          id: 'loan-sanc-01',
          implementName: 'Balón de Voleibol Mikasa V200W',
          facultyName: 'Ciencias Humanas',
          pickupLocation: 'Taquilla Bienestar Humanas (Ed. 224)',
          borrowedAt: DateTime.now().subtract(const Duration(days: 3, hours: 2)),
          returnedAt: DateTime.now().subtract(const Duration(days: 3)),
          status: LoanStatus.damaged,
          penaltyNote:
              'Válvula rota. Sanción activa (RN-03) hasta reposición o reparación en taquilla.',
        ),
        LoanRecordEntity(
          id: 'loan-prev-01',
          implementName: 'Reloj y Tablero de Ajedrez Profesional',
          facultyName: 'Ingeniería',
          pickupLocation: 'Taquilla Canchas de Minas (Ed. 411)',
          borrowedAt: DateTime.now().subtract(const Duration(days: 12)),
          returnedAt:
              DateTime.now().subtract(const Duration(days: 12, minutes: -90)),
          status: LoanStatus.returnedOnTime,
        ),
      ];
    }

    return [
      LoanRecordEntity(
        id: 'loan-act-01',
        implementName: 'Balón de Microfútbol Golty No. 4',
        facultyName: 'Ingeniería',
        pickupLocation: 'Taquilla Canchas de Minas (Ed. 411)',
        borrowedAt: DateTime.now().subtract(const Duration(days: 2)),
        returnedAt:
            DateTime.now().subtract(const Duration(days: 2, minutes: -110)),
        status: LoanStatus.returnedOnTime,
      ),
      LoanRecordEntity(
        id: 'loan-act-02',
        implementName: 'Set de Raquetas y Pelotas de Tenis de Mesa',
        facultyName: 'Ingeniería',
        pickupLocation: 'Taquilla Edificio CyT (Ed. 453)',
        borrowedAt: DateTime.now().subtract(const Duration(days: 6)),
        returnedAt:
            DateTime.now().subtract(const Duration(days: 6, minutes: -55)),
        status: LoanStatus.returnedOnTime,
      ),
      LoanRecordEntity(
        id: 'loan-act-03',
        implementName: 'Jenga Gigante de Madera',
        facultyName: 'Ciencias Humanas',
        pickupLocation: 'Taquilla Bienestar Humanas (Ed. 224)',
        borrowedAt: DateTime.now().subtract(const Duration(days: 15)),
        returnedAt:
            DateTime.now().subtract(const Duration(days: 15, minutes: -60)),
        status: LoanStatus.returnedOnTime,
      ),
    ];
  }
}
