part of '../admin_api.dart';

class EmployeeAccount {
  const EmployeeAccount({
    required this.id,
    required this.fullName,
    required this.username,
    required this.role,
    required this.roomIds,
  });
  final int id;
  final String fullName;
  final String username;
  final String role;
  final List<int> roomIds;

  factory EmployeeAccount.fromJson(Map<String, dynamic> json) =>
      EmployeeAccount(
        id: json['id'] as int,
        fullName: json['fullName'] as String,
        username: json['username'] as String,
        role: json['role'] as String,
        roomIds: (json['roomIds'] as List? ?? const []).whereType<int>().toList(
          growable: false,
        ),
      );
}
