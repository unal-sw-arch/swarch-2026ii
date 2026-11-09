/// Estados de una comanda (contratos.md §6).
class OrderStatus {
  static const pendiente = 'pendiente';
  static const enPreparacion = 'en_preparacion';
  static const listo = 'listo';
  static const entregado = 'entregado';
}

class OrderLine {
  const OrderLine({
    required this.productId,
    required this.name,
    required this.quantity,
    required this.prepMinutes,
  });

  final String productId;
  final String name;
  final int quantity;
  final int prepMinutes;

  factory OrderLine.fromJson(Map<String, dynamic> json) => OrderLine(
        productId: json['product_id'] as String? ?? '',
        name: json['name'] as String? ?? '',
        quantity: (json['quantity'] as num?)?.toInt() ?? 1,
        prepMinutes: (json['prep_minutes'] as num?)?.toInt() ?? 0,
      );
}

/// Una comanda de `GET /queue` (JSON en snake_case).
class QueueItem {
  const QueueItem({
    required this.orderId,
    required this.customerId,
    required this.orderType,
    required this.status,
    required this.prepMinutes,
    required this.priorityScore,
    required this.waitMinutes,
    required this.position,
    required this.etaMinutes,
    required this.enqueuedAt,
    this.startedAt,
    this.readyAt,
    this.items = const [],
  });

  final String orderId;
  final String customerId;
  final String orderType;
  final String status;
  final int prepMinutes;
  final double priorityScore;
  final double waitMinutes;
  final int position;
  final int etaMinutes;
  final DateTime enqueuedAt;
  final DateTime? startedAt;
  final DateTime? readyAt;
  final List<OrderLine> items;

  /// Primeros 8 caracteres del UUID, para mostrar en la comanda.
  String get shortId => orderId.length >= 8 ? orderId.substring(0, 8) : orderId;

  bool get isTakeaway => orderType == 'para_llevar';

  factory QueueItem.fromJson(Map<String, dynamic> json) => QueueItem(
        orderId: json['order_id'] as String,
        customerId: json['customer_id'] as String? ?? '',
        orderType: json['order_type'] as String? ?? '',
        status: json['status'] as String,
        prepMinutes: (json['prep_minutes'] as num?)?.toInt() ?? 0,
        priorityScore: (json['priority_score'] as num?)?.toDouble() ?? 0,
        waitMinutes: (json['wait_minutes'] as num?)?.toDouble() ?? 0,
        position: (json['position'] as num?)?.toInt() ?? 0,
        etaMinutes: (json['eta_minutes'] as num?)?.toInt() ?? 0,
        enqueuedAt: DateTime.parse(json['enqueued_at'] as String),
        startedAt: _time(json['started_at']),
        readyAt: _time(json['ready_at']),
        items: [
          for (final line in (json['items'] as List<dynamic>? ?? const []))
            OrderLine.fromJson(line as Map<String, dynamic>),
        ],
      );

  static DateTime? _time(Object? raw) => raw is String ? DateTime.tryParse(raw) : null;
}

class Capacity {
  const Capacity({required this.max, required this.inPreparation});

  final int max;
  final int inPreparation;

  bool get isFull => max > 0 && inPreparation >= max;

  factory Capacity.fromJson(Map<String, dynamic> json) => Capacity(
        max: (json['max'] as num?)?.toInt() ?? 0,
        inPreparation: (json['in_preparation'] as num?)?.toInt() ?? 0,
      );
}

/// Respuesta de `GET /queue`.
class QueueSnapshot {
  const QueueSnapshot({required this.items, required this.capacity, required this.generatedAt});

  final List<QueueItem> items;
  final Capacity capacity;
  final DateTime generatedAt;

  List<QueueItem> withStatus(String status) => [for (final i in items) if (i.status == status) i];

  factory QueueSnapshot.fromJson(Map<String, dynamic> json) => QueueSnapshot(
        items: [
          for (final item in (json['items'] as List<dynamic>? ?? const []))
            QueueItem.fromJson(item as Map<String, dynamic>),
        ],
        capacity: Capacity.fromJson(json['capacity'] as Map<String, dynamic>? ?? const {}),
        generatedAt: DateTime.tryParse(json['generated_at'] as String? ?? '') ?? DateTime.now(),
      );
}
