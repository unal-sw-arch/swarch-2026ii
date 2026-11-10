import 'dart:async';
import '../../../../core/config/app_config.dart';
import '../../domain/entities/user_entity.dart';
import '../../domain/repositories/users_repository.dart';

/// Implementacion mockeada de [UsersRepository] con perfiles predeterminados
/// para validar los flujos de Estudiantes activos, Sancionados (RN-03) e Invitados.
class MockUsersRepository implements UsersRepository {
  static const UserEntity presetStudentActive = UserEntity(
    id: 'usr-student-001',
    fullName: 'Camilo Gomez',
    email: 'cgomez@unal.edu.co',
    faculty: 'Ingenieria',
    role: UserRole.communityMember,
    status: UserStatus.active,
    avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80',
  );

  static const UserEntity presetStudentSanctioned = UserEntity(
    id: 'usr-student-002',
    fullName: 'Victor Canon',
    email: 'vcanon@unal.edu.co',
    faculty: 'Ciencias',
    role: UserRole.communityMember,
    status: UserStatus.sanctioned, // Sancionado segun RN-03
    avatarUrl: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=300&q=80',
  );

  static const UserEntity presetGuest = UserEntity(
    id: 'usr-guest-001',
    fullName: 'Visitante del Campus',
    email: 'visitante@externo.com',
    faculty: 'Externo',
    role: UserRole.guest,
    status: UserStatus.active,
  );

  UserEntity? _currentUser = presetStudentActive;

  @override
  Future<UserEntity?> getCurrentUser() async {
    await Future.delayed(const Duration(milliseconds: 200));
    return _currentUser;
  }

  @override
  Future<UserEntity> signInWithGoogle() async {
    await Future.delayed(const Duration(milliseconds: AppConfig.mockNetworkDelayMs));
    _currentUser = presetStudentActive;
    return _currentUser!;
  }

  @override
  Future<UserEntity> continueAsGuest() async {
    await Future.delayed(const Duration(milliseconds: 200));
    _currentUser = presetGuest;
    return _currentUser!;
  }

  @override
  Future<void> signOut() async {
    await Future.delayed(const Duration(milliseconds: 200));
    _currentUser = null;
  }

  @override
  Future<String?> currentToken() async => _currentUser == null ? null : 'mock-token';

  @override
  Stream<UserEntity?> authStateChanges() => Stream.value(_currentUser);

  /// Metodo de ayuda para tests o selector de perfiles en desarrollo
  void setCurrentUser(UserEntity user) {
    _currentUser = user;
  }
}
