import '../entities/user_entity.dart';

/// Contrato abstracto para autenticacion y gestion de perfil
abstract class UsersRepository {
  /// Obtiene la sesion activa actual (si existe)
  Future<UserEntity?> getCurrentUser();

  /// Inicio de sesión institucional con Google (valida @unal.edu.co, RN-01).
  /// La identidad la provee Firebase/Google, no un formulario.
  Future<UserEntity> signInWithGoogle();

  /// Inicia sesion en modo Visitante (sin credenciales)
  Future<UserEntity> continueAsGuest();

  /// Cierra la sesion
  Future<void> signOut();

  /// Token de sesión actual (Firebase JWT), o null si no hay sesión real.
  Future<String?> currentToken();

  /// Cambios de sesión: emite el usuario al entrar y null al salir.
  /// Permite que la app reaccione a expiraciones o cierres externos.
  Stream<UserEntity?> authStateChanges();
}
