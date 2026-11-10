import 'package:equatable/equatable.dart';

enum UserRole {
  communityMember, // Miembro de la Comunidad (@unal.edu.co)
  eventAdmin,      // Administrador de Eventos
  implementAdmin,  // Administrador de Implementos
  systemAdmin,     // Administrador del Sistema
  guest,           // Visitante externo sin correo institucional
}

enum UserStatus {
  active,     // Activo
  sanctioned, // Sancionado por daño o perdida (RN-03)
}

/// Representa a un usuario (Estudiante o Visitante) de UNDesparche
class UserEntity extends Equatable {
  final String id;
  final String fullName;
  final String email;
  final String? faculty;
  final UserRole role;
  final UserStatus status;
  final String? avatarUrl;

  const UserEntity({
    required this.id,
    required this.fullName,
    required this.email,
    this.faculty,
    required this.role,
    required this.status,
    this.avatarUrl,
  });

  /// Determina si es un visitante externo
  bool get isGuest => role == UserRole.guest;

  /// Determina si el usuario tiene sanciones activas
  bool get isSanctioned => status == UserStatus.sanctioned;

  /// Puede solicitar implementos si es estudiante activo y no esta sancionado
  bool get canBorrowImplements => !isGuest && status == UserStatus.active;

  UserEntity copyWith({
    String? id,
    String? fullName,
    String? email,
    String? faculty,
    UserRole? role,
    UserStatus? status,
    String? avatarUrl,
  }) {
    return UserEntity(
      id: id ?? this.id,
      fullName: fullName ?? this.fullName,
      email: email ?? this.email,
      faculty: faculty ?? this.faculty,
      role: role ?? this.role,
      status: status ?? this.status,
      avatarUrl: avatarUrl ?? this.avatarUrl,
    );
  }

  @override
  List<Object?> get props => [
    id,
    fullName,
    email,
    faculty,
    role,
    status,
    avatarUrl,
  ];
}
