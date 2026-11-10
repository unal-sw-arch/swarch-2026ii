import 'package:equatable/equatable.dart';
import '../../domain/entities/implement_entity.dart';
import '../../domain/entities/reservation_entity.dart';

abstract class InventoryState extends Equatable {
  const InventoryState();

  @override
  List<Object?> get props => [];
}

class InventoryInitial extends InventoryState {
  const InventoryInitial();
}

class InventoryLoading extends InventoryState {
  const InventoryLoading();
}

class InventoryLoaded extends InventoryState {
  final List<ImplementEntity> allImplements;
  final List<ImplementEntity> filteredImplements;
  final String selectedFaculty;
  final ImplementCategory? selectedCategory;
  final String searchQuery;
  final ReservationEntity? activeReservation;
  final int remainingSeconds;
  final bool isSubmitting;
  final String? userMessage;

  const InventoryLoaded({
    required this.allImplements,
    required this.filteredImplements,
    this.selectedFaculty = 'Todas',
    this.selectedCategory,
    this.searchQuery = '',
    this.activeReservation,
    this.remainingSeconds = 0,
    this.isSubmitting = false,
    this.userMessage,
  });

  bool get hasActiveReservation =>
      activeReservation != null &&
      activeReservation!.status == ReservationStatus.active &&
      remainingSeconds > 0;

  InventoryLoaded copyWith({
    List<ImplementEntity>? allImplements,
    List<ImplementEntity>? filteredImplements,
    String? selectedFaculty,
    ImplementCategory? Function()? selectedCategory,
    String? searchQuery,
    ReservationEntity? Function()? activeReservation,
    int? remainingSeconds,
    bool? isSubmitting,
    String? Function()? userMessage,
  }) {
    return InventoryLoaded(
      allImplements: allImplements ?? this.allImplements,
      filteredImplements: filteredImplements ?? this.filteredImplements,
      selectedFaculty: selectedFaculty ?? this.selectedFaculty,
      selectedCategory: selectedCategory != null ? selectedCategory() : this.selectedCategory,
      searchQuery: searchQuery ?? this.searchQuery,
      activeReservation: activeReservation != null ? activeReservation() : this.activeReservation,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      userMessage: userMessage != null ? userMessage() : this.userMessage,
    );
  }

  @override
  List<Object?> get props => [
    allImplements,
    filteredImplements,
    selectedFaculty,
    selectedCategory,
    searchQuery,
    activeReservation,
    remainingSeconds,
    isSubmitting,
    userMessage,
  ];
}

class InventoryError extends InventoryState {
  final String message;

  const InventoryError(this.message);

  @override
  List<Object?> get props => [message];
}
