import 'package:equatable/equatable.dart';

/// Clase base para todos los fallos tipados del dominio (Clean Architecture)
abstract class Failure extends Equatable implements Exception {
  final String message;
  final String? code;

  const Failure(this.message, {this.code});

  @override
  List<Object?> get props => [message, code];

  @override
  String toString() => message;
}

// --- FALLOS DE AUTENTICACION Y USUARIOS ---
abstract class AuthFailure extends Failure {
  const AuthFailure(super.message, {super.code});
}

class InvalidEmailDomainFailure extends AuthFailure {
  const InvalidEmailDomainFailure([
    super.message = 'Autenticación denegada. Solo se admiten correos institucionales @unal.edu.co (RN-01).',
  ]) : super(code: 'INVALID_DOMAIN');
}

class UserSanctionedFailure extends AuthFailure {
  const UserSanctionedFailure([
    super.message = 'El usuario tiene sanciones activas por daño o pérdida de implementos (RN-03).',
  ]) : super(code: 'USER_SANCTIONED');
}

class GuestAccessRestrictedFailure extends AuthFailure {
  const GuestAccessRestrictedFailure([
    super.message = 'Acción exclusiva para la comunidad universitaria UNAL (@unal.edu.co).',
  ]) : super(code: 'GUEST_RESTRICTED');
}

class ServerAuthFailure extends AuthFailure {
  const ServerAuthFailure(super.message) : super(code: 'SERVER_AUTH_ERROR');
}

// --- FALLOS DE INVENTARIO Y RESERVAS ---
abstract class InventoryFailure extends Failure {
  const InventoryFailure(super.message, {super.code});
}

class DisclaimerNotAcceptedFailure extends InventoryFailure {
  const DisclaimerNotAcceptedFailure([
    super.message = 'Debe aceptar el descargo de responsabilidad antes de solicitar el implemento (RF-20 / RF-21).',
  ]) : super(code: 'DISCLAIMER_REQUIRED');
}

class SimultaneousReservationLimitFailure extends InventoryFailure {
  const SimultaneousReservationLimitFailure([
    super.message = 'Ya tienes una reserva activa en curso. Límite máximo de 1 implemento simultáneo (RN-05).',
  ]) : super(code: 'ACTIVE_RESERVATION_LIMIT');
}

class ImplementUnavailableFailure extends InventoryFailure {
  const ImplementUnavailableFailure([
    super.message = 'El implemento no se encuentra disponible para reserva.',
  ]) : super(code: 'IMPLEMENT_UNAVAILABLE');
}

class ImplementNotFoundFailure extends InventoryFailure {
  const ImplementNotFoundFailure([
    super.message = 'El implemento solicitado no existe.',
  ]) : super(code: 'IMPLEMENT_NOT_FOUND');
}

class ServerInventoryFailure extends InventoryFailure {
  const ServerInventoryFailure(super.message) : super(code: 'SERVER_INVENTORY_ERROR');
}

// --- FALLOS DE EVENTOS ---
abstract class EventsFailure extends Failure {
  const EventsFailure(super.message, {super.code});
}

class EventNotFoundFailure extends EventsFailure {
  const EventNotFoundFailure([
    super.message = 'El evento seleccionado no existe o fue cancelado.',
  ]) : super(code: 'EVENT_NOT_FOUND');
}

class ServerEventsFailure extends EventsFailure {
  const ServerEventsFailure(super.message) : super(code: 'SERVER_EVENTS_ERROR');
}

// --- FALLOS DE RED Y CONECTIVIDAD ---
class NetworkFailure extends Failure {
  const NetworkFailure([
    super.message = 'No fue posible conectar con los servicios de UNDesparche. Comprueba tu conexión a internet.',
  ]) : super(code: 'NETWORK_ERROR');
}
