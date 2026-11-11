import 'package:equatable/equatable.dart';
import '../../domain/entities/user_entity.dart';

abstract class AuthState extends Equatable {
  const AuthState();

  @override
  List<Object?> get props => [];
}

/// Estado inicial al arrancar la aplicacion
class AuthInitial extends AuthState {
  const AuthInitial();
}

/// Estado de carga (conectando / autenticando)
class AuthLoading extends AuthState {
  const AuthLoading();
}

/// Usuario autenticado exitosamente (Estudiante o Visitante)
class Authenticated extends AuthState {
  final UserEntity user;

  const Authenticated(this.user);

  bool get isGuest => user.isGuest;
  bool get isStudent => !user.isGuest;
  bool get isSanctioned => user.isSanctioned;
  bool get canBorrow => user.canBorrowImplements;

  @override
  List<Object?> get props => [user];
}

/// Sin sesion activa (debe mostrar la pantalla de bienvenida)
class Unauthenticated extends AuthState {
  const Unauthenticated();
}

/// Error al autenticarse (ej. correo no pertenece al dominio @unal.edu.co)
class AuthError extends AuthState {
  final String message;

  const AuthError(this.message);

  @override
  List<Object?> get props => [message];
}
