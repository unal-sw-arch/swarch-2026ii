import 'package:firebase_auth/firebase_auth.dart' as fb;
import '../../../../core/auth/email_domain.dart';
import '../../../../core/config/app_config.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/api_endpoints.dart';
import '../../../../core/network/dio_client.dart';
import '../../../../core/services/token_storage.dart';
import '../../domain/entities/user_entity.dart';
import '../../domain/repositories/users_repository.dart';
import '../datasources/firebase_auth_datasource.dart';
import '../models/user_model.dart';

/// Implementación real de [UsersRepository] sobre Firebase Auth.
///
/// Flujo (igual que `AuthContext.tsx` de la web):
/// 1. Google Sign-In -> credencial de Firebase.
/// 2. Validación estricta del dominio institucional (RN-01).
/// 3. Se guarda el token (Firebase JWT) de forma segura y se inyecta en Dio.
/// 4. El perfil se enriquece desde el API Gateway (`GET /users/me/`) cuando [AppConfig.useMockData] es false.
class FirebaseUsersRepository implements UsersRepository {
  final FirebaseAuthDataSource authDataSource;
  final TokenStorage tokenStorage;
  final DioClient client;

  UserEntity? _guestOverride;

  FirebaseUsersRepository({
    required this.authDataSource,
    required this.tokenStorage,
    required this.client,
  });

  /// Mapea el usuario de Firebase a la entidad del dominio como fallback.
  UserEntity _mapFirebaseUser(fb.User user) {
    final email = user.email ?? '';
    return UserEntity(
      id: user.uid,
      fullName: user.displayName ?? email.split('@').first,
      email: email,
      role: UserRole.communityMember,
      status: UserStatus.active,
      avatarUrl: user.photoURL,
    );
  }

  Future<UserEntity> _enrichWithBackendProfile(fb.User user) async {
    final localFallback = _mapFirebaseUser(user);
    if (AppConfig.useMockData) return localFallback;

    try {
      final res = await client.dio.get(ApiEndpoints.currentUser);
      if (res.data is Map<String, dynamic>) {
        final remote = UserModel.fromJson(res.data as Map<String, dynamic>);
        return remote.copyWith(
          avatarUrl: user.photoURL ?? remote.avatarUrl,
        );
      }
    } catch (_) {
      // Fallback a los datos de Firebase si el gateway no responde
    }
    return localFallback;
  }

  void _assertInstitutionalDomain(String email) {
    if (!isInstitutionalEmail(email)) {
      throw const InvalidEmailDomainFailure();
    }
  }

  @override
  Future<UserEntity?> getCurrentUser() async {
    if (_guestOverride != null) return _guestOverride;

    final user = authDataSource.currentUser;
    if (user == null) return null;

    final email = user.email ?? '';
    // Una sesión persistida que no sea del dominio no debe considerarse válida.
    if (!isInstitutionalEmail(email)) {
      await signOut();
      return null;
    }

    // Reinyecta el token guardado en Dio para peticiones al backend.
    final token = await authDataSource.getIdToken();
    if (token != null) {
      client.updateToken(token);
      await tokenStorage.save(token);
    }
    return await _enrichWithBackendProfile(user);
  }

  @override
  Future<UserEntity> signInWithGoogle() async {
    try {
      final credential = await authDataSource.signInWithGoogle();
      final user = credential.user;
      if (user == null || (user.email ?? '').isEmpty) {
        throw const ServerAuthFailure('No se recibió la cuenta de Google.');
      }

      final email = user.email!;
      // RN-01: solo correos institucionales. Si no, cerrar la sesión abierta.
      try {
        _assertInstitutionalDomain(email);
      } on InvalidEmailDomainFailure {
        await authDataSource.signOut();
        rethrow;
      }

      _guestOverride = null;
      final token = await authDataSource.getIdToken(forceRefresh: true);
      if (token != null) {
        client.updateToken(token);
        await tokenStorage.save(token);
      }

      return await _enrichWithBackendProfile(user);
    } on GoogleSignInCancelled {
      throw const ServerAuthFailure('El inicio de sesión fue cancelado.');
    } on fb.FirebaseAuthException catch (e) {
      throw ServerAuthFailure(e.message ?? 'Error al iniciar sesión con Google.');
    }
  }

  @override
  Future<UserEntity> continueAsGuest() async {
    const guest = UserEntity(
      id: 'usr-guest',
      fullName: 'Visitante del Campus',
      email: 'visitante@externo.com',
      faculty: 'Externo',
      role: UserRole.guest,
      status: UserStatus.active,
    );
    _guestOverride = guest;
    client.updateToken(null);
    await tokenStorage.clear();
    return guest;
  }

  @override
  Future<void> signOut() async {
    _guestOverride = null;
    await authDataSource.signOut();
    client.updateToken(null);
    await tokenStorage.clear();
  }

  @override
  Future<String?> currentToken() => authDataSource.getIdToken();

  @override
  Stream<UserEntity?> authStateChanges() {
    return authDataSource.authStateChanges().asyncMap((user) async {
      if (_guestOverride != null) return _guestOverride;
      if (user == null) return null;
      final email = user.email ?? '';
      if (!isInstitutionalEmail(email)) {
        return null;
      }
      return _enrichWithBackendProfile(user);
    });
  }
}
