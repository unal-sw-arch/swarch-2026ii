class ClientUser {
  const ClientUser({
    required this.id,
    required this.fullName,
    required this.username,
    required this.role,
  });

  factory ClientUser.fromJson(Map<String, dynamic> json) => ClientUser(
    id: json['id'] as int,
    fullName: json['fullName'] as String,
    username: json['username'] as String,
    role: json['role'] as String,
  );

  final int id;
  final String fullName;
  final String username;
  final String role;
}

class ClientSession {
  const ClientSession({
    required this.token,
    required this.expiresAt,
    required this.user,
  });

  final String token;
  final DateTime expiresAt;
  final ClientUser user;
}
