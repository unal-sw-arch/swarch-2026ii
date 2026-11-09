import 'package:flutter/material.dart';

import '../models/queue_item.dart';

class StatusButtons extends StatelessWidget {
  const StatusButtons({
    super.key,
    required this.item,
    required this.busy,
    required this.onStart,
    required this.onReady,
  });

  final QueueItem item;
  final bool busy;
  final VoidCallback onStart;
  final VoidCallback onReady;

  @override
  Widget build(BuildContext context) {
    switch (item.status) {
      case OrderStatus.pendiente:
        return FilledButton.icon(
          key: Key('start-${item.orderId}'),
          onPressed: busy ? null : onStart,
          icon: const Icon(Icons.local_fire_department),
          label: const Text('En preparación'),
        );
      case OrderStatus.enPreparacion:
        return FilledButton.icon(
          key: Key('ready-${item.orderId}'),
          onPressed: busy ? null : onReady,
          style: FilledButton.styleFrom(backgroundColor: Colors.green.shade700),
          icon: const Icon(Icons.check_circle),
          label: const Text('Listo'),
        );
      default:
        return const SizedBox.shrink();
    }
  }
}
