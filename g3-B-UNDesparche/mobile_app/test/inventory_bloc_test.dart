import 'package:flutter_test/flutter_test.dart';
import 'package:bloc_test/bloc_test.dart';
import 'package:mobile_app/features/inventory/domain/entities/implement_entity.dart';
import 'package:mobile_app/features/inventory/domain/entities/reservation_entity.dart';
import 'package:mobile_app/features/inventory/domain/repositories/inventory_repository.dart';
import 'package:mobile_app/features/inventory/presentation/bloc/inventory_bloc.dart';
import 'package:mobile_app/features/inventory/presentation/bloc/inventory_event.dart';
import 'package:mobile_app/features/inventory/presentation/bloc/inventory_state.dart';

class _ImmediateInventoryRepository implements InventoryRepository {
  List<ImplementEntity> items = [
    const ImplementEntity(
      id: 'imp-001',
      name: 'Balon de Microfutbol Golty',
      category: ImplementCategory.balls,
      facultyName: 'Ingenieria',
      pickupLocation: 'Taquilla Ed. 411',
      status: ImplementStatus.available,
      usageTimeMinutes: 120,
      description: 'Balon de microfutbol',
    ),
    const ImplementEntity(
      id: 'imp-002',
      name: 'Juego de Jenga',
      category: ImplementCategory.boardGames,
      facultyName: 'Ciencias Humanas',
      pickupLocation: 'Taquilla Ed. 224',
      status: ImplementStatus.available,
      usageTimeMinutes: 60,
      description: 'Juego de mesa',
    ),
  ];

  ReservationEntity? activeRes;

  @override
  Future<List<ImplementEntity>> getImplements({String? facultyName, ImplementCategory? category}) async {
    return List.from(items);
  }

  @override
  Future<ImplementEntity> getImplementById(String implementId) async {
    return items.firstWhere((i) => i.id == implementId);
  }

  @override
  Future<ReservationEntity> createReservation({
    required String implementId,
    required String userId,
    required String userEmail,
    required bool disclaimerAccepted,
  }) async {
    if (!disclaimerAccepted) throw Exception('Debe aceptar el descargo de responsabilidad');
    if (!userEmail.endsWith('@unal.edu.co')) throw Exception('Solo correos @unal.edu.co');

    final res = ReservationEntity(
      id: 'res-123',
      implementId: implementId,
      implementName: 'Balon de Microfutbol Golty',
      pickupLocation: 'Taquilla Ed. 411',
      userId: userId,
      userEmail: userEmail,
      reservedAt: DateTime.now(),
      expiresAt: DateTime.now().add(const Duration(minutes: 10)),
      status: ReservationStatus.active,
      disclaimerAccepted: true,
    );
    activeRes = res;
    return res;
  }

  @override
  Future<void> cancelReservation(String reservationId) async {
    activeRes = null;
  }

  @override
  Future<ReservationEntity?> getActiveReservation(String userId) async {
    return activeRes;
  }

  @override
  Stream<ReservationEntity?> watchActiveReservation(String userId) {
    return Stream.value(activeRes);
  }

  @override
  Future<void> dispose() async {}
}

void main() {
  late _ImmediateInventoryRepository repository;
  late InventoryBloc inventoryBloc;

  setUp(() {
    repository = _ImmediateInventoryRepository();
    inventoryBloc = InventoryBloc(inventoryRepository: repository);
  });

  tearDown(() {
    inventoryBloc.close();
  });

  group('Pruebas de InventoryBloc (Fase 4: Inventario y Temporizador RN-02)', () {
    test('Estado inicial es InventoryInitial', () {
      expect(inventoryBloc.state, equals(const InventoryInitial()));
    });

    blocTest<InventoryBloc, InventoryState>(
      'Carga todos los implementos al disparar LoadInventoryRequested',
      build: () => inventoryBloc,
      act: (bloc) => bloc.add(const LoadInventoryRequested()),
      expect: () => [
        const InventoryLoading(),
        isA<InventoryLoaded>().having((s) => s.allImplements.length, 'tiene 2 implementos', 2),
      ],
    );

    blocTest<InventoryBloc, InventoryState>(
      'Filtra implementos por Facultad de Ingenieria (RF-8)',
      build: () => inventoryBloc,
      seed: () => InventoryLoaded(
        allImplements: repository.items,
        filteredImplements: repository.items,
      ),
      act: (bloc) => bloc.add(const FilterInventoryFacultyChanged('Ingenieria')),
      expect: () => [
        isA<InventoryLoaded>().having((s) => s.filteredImplements.length, 'solo 1 de ingenieria', 1),
      ],
    );

    blocTest<InventoryBloc, InventoryState>(
      'Filtra implementos por categoria Juegos de Mesa (RF-7)',
      build: () => inventoryBloc,
      seed: () => InventoryLoaded(
        allImplements: repository.items,
        filteredImplements: repository.items,
      ),
      act: (bloc) => bloc.add(const FilterInventoryCategoryChanged(ImplementCategory.boardGames)),
      expect: () => [
        isA<InventoryLoaded>().having((s) => s.filteredImplements.length, 'solo 1 juego de mesa', 1),
      ],
    );

    blocTest<InventoryBloc, InventoryState>(
      'Crea reserva con temporizador de 10 minutos (RN-02 y RF-21)',
      build: () => inventoryBloc,
      seed: () => InventoryLoaded(
        allImplements: repository.items,
        filteredImplements: repository.items,
      ),
      act: (bloc) => bloc.add(const CreateReservationRequested(
        implementId: 'imp-001',
        userId: 'usr-student-001',
        userEmail: 'estudiante@unal.edu.co',
        disclaimerAccepted: true,
      )),
      expect: () => [
        isA<InventoryLoaded>().having((s) => s.isSubmitting, 'enviando', true),
        isA<InventoryLoaded>()
            .having((s) => s.activeReservation != null, 'tiene reserva activa', true)
            .having((s) => s.remainingSeconds, 'cuenta regresiva activa', inInclusiveRange(590, 600)),
      ],
    );

    blocTest<InventoryBloc, InventoryState>(
      'Carga reserva activa dinámicamente según el userId proporcionado en LoadInventoryRequested',
      build: () => inventoryBloc,
      act: (bloc) async {
        repository.activeRes = ReservationEntity(
          id: 'res-custom-01',
          implementId: 'imp-001',
          implementName: 'Balon de Microfutbol Golty',
          pickupLocation: 'Taquilla Ed. 411',
          userId: 'usr-student-999',
          userEmail: 'custom@unal.edu.co',
          reservedAt: DateTime.now(),
          expiresAt: DateTime.now().add(const Duration(minutes: 8)),
          status: ReservationStatus.active,
          disclaimerAccepted: true,
        );
        bloc.add(const LoadInventoryRequested(userId: 'usr-student-999'));
      },
      expect: () => [
        const InventoryLoading(),
        isA<InventoryLoaded>()
            .having((s) => s.activeReservation?.userId, 'id dinamico del usuario', 'usr-student-999')
            .having((s) => s.remainingSeconds, 'segundos restantes', inInclusiveRange(470, 480)),
      ],
    );
  });
}
