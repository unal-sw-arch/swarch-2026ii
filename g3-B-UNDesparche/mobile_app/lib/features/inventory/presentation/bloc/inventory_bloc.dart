import 'dart:async';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/implement_entity.dart';
import '../../domain/entities/reservation_entity.dart';
import '../../domain/repositories/inventory_repository.dart';
import '../../domain/usecases/filter_implements.dart';
import 'inventory_event.dart';
import 'inventory_state.dart';

/// BLoC que coordina el inventario por facultades y la regla del temporizador de 10 minutos (RN-02)
class InventoryBloc extends Bloc<InventoryEvent, InventoryState> {
  final InventoryRepository inventoryRepository;
  Timer? _countdownTimer;
  StreamSubscription<ReservationEntity?>? _reservationSubscription;

  InventoryBloc({required this.inventoryRepository}) : super(const InventoryInitial()) {
    on<LoadInventoryRequested>(_onLoadInventoryRequested);
    on<FilterInventoryFacultyChanged>(_onFilterFacultyChanged);
    on<FilterInventoryCategoryChanged>(_onFilterCategoryChanged);
    on<SearchInventoryQueryChanged>(_onSearchQueryChanged);
    on<CreateReservationRequested>(_onCreateReservationRequested);
    on<CancelReservationRequested>(_onCancelReservationRequested);
    on<ActiveReservationUpdated>(_onActiveReservationUpdated);
    on<ReservationTickEvent>(_onReservationTick);
  }

  @override
  Future<void> close() {
    _countdownTimer?.cancel();
    _reservationSubscription?.cancel();
    return super.close();
  }

  Future<void> _onLoadInventoryRequested(
    LoadInventoryRequested event,
    Emitter<InventoryState> emit,
  ) async {
    emit(const InventoryLoading());
    try {
      final items = await inventoryRepository.getImplements();
      final faculty = event.facultyName ?? 'Todas';
      final category = event.category;
      final query = event.searchQuery ?? '';

      final filtered = _filter(items, faculty, category, query);
      final currentUserId = event.userId ?? 'usr-student-001';
      final active = await inventoryRepository.getActiveReservation(currentUserId);

      if (active != null && active.status == ReservationStatus.active && !active.isExpired) {
        _startCountdown(active.remainingSeconds);
      }

      emit(InventoryLoaded(
        allImplements: items,
        filteredImplements: filtered,
        selectedFaculty: faculty,
        selectedCategory: category,
        searchQuery: query,
        activeReservation: active,
        remainingSeconds: active?.remainingSeconds ?? 0,
      ));
    } on Failure catch (failure) {
      emit(InventoryError('Error al cargar inventario: ${failure.message}'));
    } catch (e) {
      emit(InventoryError('Error al cargar inventario: ${e.toString().replaceAll('Exception: ', '')}'));
    }
  }

  void _onFilterFacultyChanged(
    FilterInventoryFacultyChanged event,
    Emitter<InventoryState> emit,
  ) {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      final filtered = _filter(current.allImplements, event.facultyName, current.selectedCategory, current.searchQuery);
      emit(current.copyWith(
        selectedFaculty: event.facultyName,
        filteredImplements: filtered,
      ));
    }
  }

  void _onFilterCategoryChanged(
    FilterInventoryCategoryChanged event,
    Emitter<InventoryState> emit,
  ) {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      final filtered = _filter(current.allImplements, current.selectedFaculty, event.category, current.searchQuery);
      emit(current.copyWith(
        selectedCategory: () => event.category,
        filteredImplements: filtered,
      ));
    }
  }

  void _onSearchQueryChanged(
    SearchInventoryQueryChanged event,
    Emitter<InventoryState> emit,
  ) {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      final filtered = _filter(current.allImplements, current.selectedFaculty, current.selectedCategory, event.query);
      emit(current.copyWith(
        searchQuery: event.query,
        filteredImplements: filtered,
      ));
    }
  }

  Future<void> _onCreateReservationRequested(
    CreateReservationRequested event,
    Emitter<InventoryState> emit,
  ) async {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      emit(current.copyWith(isSubmitting: true));

      try {
        final reservation = await inventoryRepository.createReservation(
          implementId: event.implementId,
          userId: event.userId,
          userEmail: event.userEmail,
          disclaimerAccepted: event.disclaimerAccepted,
        );

        // Actualizar estado del implemento a Reservado
        final updatedItems = current.allImplements.map((i) {
          return i.id == event.implementId ? i.copyWith(status: ImplementStatus.reserved) : i;
        }).toList();

        final filtered = _filter(updatedItems, current.selectedFaculty, current.selectedCategory, current.searchQuery);

        _startCountdown(reservation.remainingSeconds);

        emit(current.copyWith(
          allImplements: updatedItems,
          filteredImplements: filtered,
          activeReservation: () => reservation,
          remainingSeconds: reservation.remainingSeconds,
          isSubmitting: false,
          userMessage: () => '¡Reserva exitosa! Tienes 10 min para reclamar.',
        ));
      } on Failure catch (failure) {
        emit(current.copyWith(
          isSubmitting: false,
          userMessage: () => failure.message,
        ));
      } catch (e) {
        emit(current.copyWith(
          isSubmitting: false,
          userMessage: () => e.toString().replaceAll('Exception: ', ''),
        ));
      }
    }
  }

  Future<void> _onCancelReservationRequested(
    CancelReservationRequested event,
    Emitter<InventoryState> emit,
  ) async {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      emit(current.copyWith(isSubmitting: true));

      try {
        await inventoryRepository.cancelReservation(event.reservationId);
        _countdownTimer?.cancel();

        // Liberar implemento en lista local
        final activeRes = current.activeReservation;
        final updatedItems = current.allImplements.map((i) {
          return (activeRes != null && i.id == activeRes.implementId)
              ? i.copyWith(status: ImplementStatus.available)
              : i;
        }).toList();

        final filtered = _filter(updatedItems, current.selectedFaculty, current.selectedCategory, current.searchQuery);

        emit(current.copyWith(
          allImplements: updatedItems,
          filteredImplements: filtered,
          activeReservation: () => null,
          remainingSeconds: 0,
          isSubmitting: false,
          userMessage: () => 'Reserva cancelada correctamente.',
        ));
      } catch (e) {
        emit(current.copyWith(
          isSubmitting: false,
          userMessage: () => 'No fue posible cancelar la reserva: $e',
        ));
      }
    }
  }

  void _onActiveReservationUpdated(
    ActiveReservationUpdated event,
    Emitter<InventoryState> emit,
  ) {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      if (event.reservation == null) {
        _countdownTimer?.cancel();
        emit(current.copyWith(activeReservation: () => null, remainingSeconds: 0));
      } else {
        _startCountdown(event.reservation!.remainingSeconds);
        emit(current.copyWith(
          activeReservation: () => event.reservation,
          remainingSeconds: event.reservation!.remainingSeconds,
        ));
      }
    }
  }

  void _onReservationTick(
    ReservationTickEvent event,
    Emitter<InventoryState> emit,
  ) {
    if (state is InventoryLoaded) {
      final current = state as InventoryLoaded;
      if (event.remainingSeconds <= 0) {
        _countdownTimer?.cancel();
        // Expirado (RN-02)
        emit(current.copyWith(
          activeReservation: () => null,
          remainingSeconds: 0,
          userMessage: () => 'Tu tiempo de 10 minutos expiró. El implemento fue liberado.',
        ));
      } else {
        emit(current.copyWith(remainingSeconds: event.remainingSeconds));
      }
    }
  }

  void _startCountdown(int initialSeconds) {
    _countdownTimer?.cancel();
    int seconds = initialSeconds;

    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      seconds--;
      add(ReservationTickEvent(seconds));
      if (seconds <= 0) {
        timer.cancel();
      }
    });
  }

  List<ImplementEntity> _filter(
    List<ImplementEntity> list,
    String faculty,
    ImplementCategory? category,
    String query,
  ) {
    return ImplementFilter(faculty: faculty, category: category, query: query)
        .apply(list);
  }
}
