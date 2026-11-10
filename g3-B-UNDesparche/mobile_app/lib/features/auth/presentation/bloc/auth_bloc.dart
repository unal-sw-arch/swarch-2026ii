import 'dart:async';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/error/failures.dart';
import '../../domain/entities/user_entity.dart';
import '../../domain/repositories/users_repository.dart';
import 'auth_event.dart';
import 'auth_state.dart';

/// BLoC que gestiona el ciclo de vida de la sesion de estudiantes y visitantes
class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final UsersRepository usersRepository;

  /// Limpieza de recursos de otros módulos al cerrar sesión (p. ej. cerrar el
  /// stream de reservas del inventario, #11). Se inyecta para no acoplar auth
  /// con inventory.
  final Future<void> Function()? onSignOut;

  StreamSubscription<UserEntity?>? _sessionSub;
  bool _signingOut = false;

  AuthBloc({required this.usersRepository, this.onSignOut}) : super(const AuthInitial()) {
    on<AuthCheckRequested>(_onAuthCheckRequested);
    on<AuthGoogleSignInRequested>(_onAuthGoogleSignInRequested);
    on<AuthGuestSignInRequested>(_onAuthGuestSignInRequested);
    on<AuthSessionChanged>(_onAuthSessionChanged);
    on<AuthSignOutRequested>(_onAuthSignOutRequested);

    // Reacciona a expiraciones o cierres de sesión externos (#3).
    _sessionSub = usersRepository.authStateChanges().listen((user) {
      add(AuthSessionChanged(user));
    });
  }

  @override
  Future<void> close() {
    _sessionSub?.cancel();
    return super.close();
  }

  Future<void> _onAuthCheckRequested(
    AuthCheckRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    try {
      final user = await usersRepository.getCurrentUser();
      if (user != null) {
        emit(Authenticated(user));
      } else {
        emit(const Unauthenticated());
      }
    } catch (e) {
      emit(const Unauthenticated());
    }
  }

  Future<void> _onAuthGoogleSignInRequested(
    AuthGoogleSignInRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    try {
      final user = await usersRepository.signInWithGoogle();
      emit(Authenticated(user));
    } on Failure catch (failure) {
      emit(AuthError(failure.message));
    } catch (e) {
      emit(AuthError(e.toString().replaceAll('Exception: ', '')));
    }
  }

  Future<void> _onAuthGuestSignInRequested(
    AuthGuestSignInRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoading());
    try {
      final guest = await usersRepository.continueAsGuest();
      emit(Authenticated(guest));
    } catch (e) {
      emit(AuthError('No fue posible iniciar en modo invitado: $e'));
    }
  }

  void _onAuthSessionChanged(
    AuthSessionChanged event,
    Emitter<AuthState> emit,
  ) {
    // No pisar un cierre de sesión en curso ni el estado inicial de carga.
    if (_signingOut) return;
    if (event.user != null) {
      emit(Authenticated(event.user!));
    } else if (state is Authenticated) {
      // La sesión dejó de ser válida (expiración, cierre externo).
      emit(const Unauthenticated());
    }
  }

  Future<void> _onAuthSignOutRequested(
    AuthSignOutRequested event,
    Emitter<AuthState> emit,
  ) async {
    _signingOut = true;
    emit(const AuthLoading());
    try {
      await usersRepository.signOut();
      await onSignOut?.call();
      emit(const Unauthenticated());
    } catch (e) {
      emit(const Unauthenticated());
    } finally {
      _signingOut = false;
    }
  }
}
