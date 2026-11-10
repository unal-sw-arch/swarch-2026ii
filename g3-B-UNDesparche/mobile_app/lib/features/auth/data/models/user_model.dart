import '../../../../core/utils/json_coerce.dart';
import '../../domain/entities/user_entity.dart';

/// Modelo de datos (DTO) para la serialización y deserialización de usuarios
class UserModel extends UserEntity {
  const UserModel({
    required super.id,
    required super.fullName,
    required super.email,
    super.faculty,
    required super.role,
    required super.status,
    super.avatarUrl,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    final statusStr = asString(json['status']).toUpperCase();
    final isSanctioned = statusStr == 'SAN' || statusStr == 'SANCTIONED' || json['is_sanctioned'] == true;
    return UserModel(
      id: asString(json['id'] ?? json['firebase_uid']),
      fullName: asString(json['full_name'] ?? json['name']),
      email: asString(json['email']),
      faculty: asString(json['faculty'], 'Ingenieria'),
      role: _parseRole(json['role']),
      status: isSanctioned ? UserStatus.sanctioned : UserStatus.active,
      avatarUrl: asStringOrNull(json['avatar_url'] ?? json['photo_url']),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'firebase_uid': id,
      'full_name': fullName,
      'email': email,
      'faculty': faculty,
      'role': role.name,
      'status': status.name,
      'avatar_url': avatarUrl,
    };
  }

  static UserRole _parseRole(dynamic role) {
    final str = role?.toString().toLowerCase() ?? '';
    if (str.contains('event')) return UserRole.eventAdmin;
    if (str.contains('implement')) return UserRole.implementAdmin;
    if (str.contains('system') || str.contains('sys_admin') || str.contains('sysadmin')) return UserRole.systemAdmin;
    if (str.contains('guest')) return UserRole.guest;
    return UserRole.communityMember;
  }
}
