part of '../admin_api.dart';

class RegisteredAdmin {
  const RegisteredAdmin({
    required this.id,
    required this.fullName,
    required this.username,
    required this.role,
  });

  final int id;
  final String fullName;
  final String username;
  final String role;
}

class AdminSession {
  const AdminSession({
    required this.token,
    required this.expiresAt,
    required this.admin,
  });
  final String token;
  final DateTime expiresAt;
  final RegisteredAdmin admin;
}

class AdminActivity {
  const AdminActivity({
    required this.id,
    required this.author,
    required this.type,
    required this.modification,
    required this.roomId,
    required this.createdAt,
  });

  factory AdminActivity.fromJson(Map<String, dynamic> json) => AdminActivity(
    id: json['id'] as int,
    author: json['author'] as String,
    type: json['type'] as String,
    modification: json['modification'] as String,
    roomId: (json['roomId'] ?? json['hallId']) as int?,
    createdAt: DateTime.parse(json['createdAt'] as String),
  );

  final int id;
  final String author;
  final String type;
  final String modification;
  final int? roomId;
  final DateTime createdAt;
}

class AdminRevenuePoint {
  const AdminRevenuePoint({required this.label, required this.value});
  factory AdminRevenuePoint.fromJson(Map<String, dynamic> json) =>
      AdminRevenuePoint(
        label: json['label'] as String,
        value: (json['value'] as num).toDouble(),
      );
  final String label;
  final double value;
}

class AdminOverviewMetrics {
  const AdminOverviewMetrics({
    required this.salesToday,
    required this.ordersToday,
    required this.averageTicket,
    required this.points,
    required this.topProduct,
    required this.categories,
  });
  factory AdminOverviewMetrics.fromJson(Map<String, dynamic> json) =>
      AdminOverviewMetrics(
        salesToday: (json['salesToday'] as num).round(),
        ordersToday: (json['ordersToday'] as num).round(),
        averageTicket: (json['averageTicket'] as num).round(),
        points: (json['points'] as List)
            .map(
              (value) =>
                  AdminRevenuePoint.fromJson(value as Map<String, dynamic>),
            )
            .toList(),
        topProduct: json['topProduct'] is Map<String, dynamic>
            ? AdminTopProduct.fromJson(
                json['topProduct'] as Map<String, dynamic>,
              )
            : null,
        categories: (json['categories'] as List? ?? const [])
            .map(
              (value) =>
                  AdminCategoryMetric.fromJson(value as Map<String, dynamic>),
            )
            .toList(),
      );
  final int salesToday;
  final int ordersToday;
  final int averageTicket;
  final List<AdminRevenuePoint> points;
  final AdminTopProduct? topProduct;
  final List<AdminCategoryMetric> categories;
}

class AdminTopProduct {
  const AdminTopProduct({
    required this.name,
    required this.value,
    required this.quantity,
  });
  factory AdminTopProduct.fromJson(Map<String, dynamic> json) =>
      AdminTopProduct(
        name: json['name'] as String,
        value: (json['value'] as num).round(),
        quantity: (json['quantity'] as num).round(),
      );
  final String name;
  final int value;
  final int quantity;
}

class AdminCategoryMetric {
  const AdminCategoryMetric({required this.name, required this.value});
  factory AdminCategoryMetric.fromJson(Map<String, dynamic> json) =>
      AdminCategoryMetric(
        name: json['name'] as String,
        value: (json['value'] as num).toDouble(),
      );
  final String name;
  final double value;
}

class AdminOrderHistorySummary {
  const AdminOrderHistorySummary({
    required this.waiting,
    required this.eating,
    required this.closed,
  });

  factory AdminOrderHistorySummary.fromJson(Map<String, dynamic> json) =>
      AdminOrderHistorySummary(
        waiting: (json['waiting'] as num?)?.toInt() ?? 0,
        eating: (json['eating'] as num?)?.toInt() ?? 0,
        closed: (json['closed'] as num?)?.toInt() ?? 0,
      );

  final int waiting;
  final int eating;
  final int closed;

  int countFor(String status) => switch (status) {
    'waiting' => waiting,
    'eating' => eating,
    'closed' => closed,
    _ => 0,
  };
}

class AdminOrderHistoryEntry {
  const AdminOrderHistoryEntry({
    required this.id,
    required this.label,
    required this.status,
    required this.totalValue,
    required this.paymentMethod,
    required this.updatedAt,
  });

  factory AdminOrderHistoryEntry.fromJson(Map<String, dynamic> json) =>
      AdminOrderHistoryEntry(
        id: (json['id'] as num).toInt(),
        label: json['label'] as String,
        status: json['status'] as String,
        totalValue: (json['totalValue'] as num?)?.toInt() ?? 0,
        paymentMethod: json['paymentMethod'] as String?,
        updatedAt: DateTime.parse(json['updatedAt'] as String),
      );

  final int id;
  final String label;
  final String status;
  final int totalValue;
  final String? paymentMethod;
  final DateTime updatedAt;
}
