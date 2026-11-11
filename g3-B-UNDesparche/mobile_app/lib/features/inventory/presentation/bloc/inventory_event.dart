import 'package:equatable/equatable.dart';
import '../../domain/entities/implement_entity.dart';
import '../../domain/entities/reservation_entity.dart';

abstract class InventoryEvent extends Equatable {
  const InventoryEvent();

  @override
  List<Object?> get props => [];
}

/// Carga los implementos del catalogo y consulta si hay reserva activa
class LoadInventoryRequested extends InventoryEvent {
  final String? facultyName;
  final ImplementCategory? category;
  final String? searchQuery;
  final String? userId;

  const LoadInventoryRequested({
    this.facultyName,
    this.category,
    this.searchQuery,
    this.userId,
  });

  @override
  List<Object?> get props => [facultyName, category, searchQuery, userId];
}

/// Filtra por facultad especifica (RF-8)
class FilterInventoryFacultyChanged extends InventoryEvent {
  final String facultyName;

  const FilterInventoryFacultyChanged(this.facultyName);

  @override
  List<Object?> get props => [facultyName];
}

/// Filtra por categoria de implemento (RF-7)
class FilterInventoryCategoryChanged extends InventoryEvent {
  final ImplementCategory? category;

  const FilterInventoryCategoryChanged(this.category);

  @override
  List<Object?> get props => [category];
}

/// Busqueda de implemento por texto en tiempo real
class SearchInventoryQueryChanged extends InventoryEvent {
  final String query;

  const SearchInventoryQueryChanged(this.query);

  @override
  List<Object?> get props => [query];
}

/// Crea una reserva con la ventana de 10 minutos (RN-02, RF-20, RF-21)
class CreateReservationRequested extends InventoryEvent {
  final String implementId;
  final String userId;
  final String userEmail;
  final bool disclaimerAccepted;

  const CreateReservationRequested({
    required this.implementId,
    required this.userId,
    required this.userEmail,
    required this.disclaimerAccepted,
  });

  @override
  List<Object?> get props => [implementId, userId, userEmail, disclaimerAccepted];
}

/// Cancela la reserva activa y libera el implemento
class CancelReservationRequested extends InventoryEvent {
  final String reservationId;

  const CancelReservationRequested(this.reservationId);

  @override
  List<Object?> get props => [reservationId];
}

/// Notificacion interna cuando el stream de la reserva cambia
class ActiveReservationUpdated extends InventoryEvent {
  final ReservationEntity? reservation;

  const ActiveReservationUpdated(this.reservation);

  @override
  List<Object?> get props => [reservation];
}

/// Tick del reloj para decrementar el contador en vivo cada segundo
class ReservationTickEvent extends InventoryEvent {
  final int remainingSeconds;

  const ReservationTickEvent(this.remainingSeconds);

  @override
  List<Object?> get props => [remainingSeconds];
}
