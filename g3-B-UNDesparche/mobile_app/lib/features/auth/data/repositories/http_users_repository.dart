import 'package:dio/dio.dart';
import '../../../../core/error/failures.dart';
import '../../../../core/network/api_endpoints.dart';
import '../../../../core/network/dio_client.dart';
import '../../domain/entities/user_entity.dart';
import '../../domain/repositories/users_repository.dart';
import '../models/user_model.dart';

/// Perfil remoto desde users_service. La autenticación en sí la hace Firebase
/// ([FirebaseUsersRepository]); este repo solo se usa cuando el gateway esté
/// disponible, para traer el perfil del backend usando el token de Firebase.
class HttpUsersRepository implements UsersRepository {
  final DioClient client;
  UserEntity? _currentUser;

  HttpUsersRepository({required this.client});

  @override
  Future<UserEntity?> getCurrentUser() async {
    try {
      final response = await client.dio.get(ApiEndpoints.currentUser);
      _currentUser = UserModel.fromJson(response.data as Map<String, dynamic>);
      return _currentUser;
    } on DioException {
      return _currentUser;
    }
  }

  @override
  Future<UserEntity> signInWithGoogle() async {
    // Sin Firebase este repo no inicia sesión por sí solo; el backend confía en
    // el token de Firebase ya inyectado en Dio. Se consulta el perfil actual.
    final user = await getCurrentUser();
    if (user == null) {
      throw const ServerAuthFailure('No hay sesión de Firebase activa.');
    }
    return user;
  }

  @override
  Future<UserEntity> continueAsGuest() async {
    _currentUser = const UserModel(
      id: 'usr-guest',
      fullName: 'Visitante del Campus',
      email: 'visitante@externo.com',
      role: UserRole.guest,
      status: UserStatus.active,
    );
    client.updateToken(null);
    return _currentUser!;
  }

  @override
  Future<void> signOut() async {
    _currentUser = null;
    client.updateToken(null);
  }

  @override
  Future<String?> currentToken() async => null;

  @override
  Stream<UserEntity?> authStateChanges() => Stream.value(_currentUser);
}
