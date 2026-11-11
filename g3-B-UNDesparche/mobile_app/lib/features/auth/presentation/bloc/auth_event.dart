import 'package:equatable/equatable.dart';
import '../../domain/entities/user_entity.dart';

abstract class AuthEvent extends Equatable {
  const AuthEvent();

  @override
  List<Object?> get props => [];
}

/// Comprueba si ya existe una sesion activa en el dispositivo
class AuthCheckRequested extends AuthEvent {
  const AuthCheckRequested();
}

/// Inicio de sesión institucional con Google (debe terminar en @unal.edu.co, RN-01).
/// La identidad la provee Firebase/Google.
class AuthGoogleSignInRequested extends AuthEvent {
  const AuthGoogleSignInRequested();
}

/// Acceso inmediato sin credenciales para visitantes del campus
class AuthGuestSignInRequested extends AuthEvent {
  const AuthGuestSignInRequested();
}

/// La sesión cambió externamente (expiración, cierre desde otro lugar).
class AuthSessionChanged extends AuthEvent {
  final UserEntity? user;

  const AuthSessionChanged(this.user);

  @override
  List<Object?> get props => [user];
}

/// Cierre de sesion
class AuthSignOutRequested extends AuthEvent {
  const AuthSignOutRequested();
}
