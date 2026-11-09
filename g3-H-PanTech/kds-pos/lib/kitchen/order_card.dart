import 'package:flutter/material.dart';

import '../models/queue_item.dart';

/// Tarjeta de comanda: posición, tipo, productos, espera, ETA y, si corresponde, un botón de acción.
class OrderCard extends StatelessWidget {
  const OrderCard({super.key, required this.item, this.action});

  final QueueItem item;

  /// Botones de acción (cocina o despacho); `null` si la persona no tiene permiso.
  final Widget? action;

  static Color statusColor(String status) => switch (status) {
        OrderStatus.enPreparacion => Colors.orange.shade800,
        OrderStatus.listo => Colors.green.shade700,
        _ => Colors.blueGrey.shade600,
      };

  static String statusLabel(String status) => switch (status) {
        OrderStatus.pendiente => 'Pendiente',
        OrderStatus.enPreparacion => 'En preparación',
        OrderStatus.listo => 'Listo',
        OrderStatus.entregado => 'Entregado',
        _ => status,
      };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = statusColor(item.status);
    return SizedBox(
      width: 340,
      child: Card(
        key: Key('card-${item.orderId}'),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: BorderSide(color: color, width: 2),
        ),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  if (item.position > 0)
                    Text('#${item.position}', style: theme.textTheme.headlineSmall?.copyWith(color: color)),
                  const SizedBox(width: 8),
                  Expanded(child: Text('Pedido ${item.shortId}', style: theme.textTheme.titleMedium)),
                  Chip(
                    label: Text(item.isTakeaway ? 'Para llevar' : 'En mesa'),
                    visualDensity: VisualDensity.compact,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                OrderCard.statusLabel(item.status),
                style: theme.textTheme.labelLarge?.copyWith(color: color, fontWeight: FontWeight.bold),
              ),
              const Divider(),
              if (item.items.isEmpty)
                const Text('Sin detalle de productos')
              else
                for (final line in item.items) Text('${line.quantity} × ${line.name}'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 12,
                children: [
                  _metric(Icons.hourglass_bottom, 'Espera ${item.waitMinutes.round()} min'),
                  if (item.status != OrderStatus.listo) _metric(Icons.timer_outlined, 'ETA ${item.etaMinutes} min'),
                  _metric(Icons.restaurant, 'Prep. ${item.prepMinutes} min'),
                ],
              ),
              if (action != null) ...[const SizedBox(height: 10), Align(alignment: Alignment.centerRight, child: action)],
            ],
          ),
        ),
      ),
    );
  }

  Widget _metric(IconData icon, String text) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [Icon(icon, size: 16), const SizedBox(width: 4), Text(text)],
      );
}
