import 'package:flutter/material.dart';

import '../auth/session_store.dart';
import '../kitchen/order_card.dart';
import '../kitchen/queue_controller.dart';
import '../models/queue_item.dart';
import '../widgets/status_banner.dart';

/// Despacho: pedidos listos separados por tipo, con el botón *Entregar*.
class DispatchPage extends StatelessWidget {
  const DispatchPage({super.key, required this.controller, required this.session});

  final QueueController controller;
  final Session session;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final snapshot = controller.snapshot;
        return Column(
          children: [
            if (controller.error != null)
              StatusBanner(error: controller.error!, lastUpdated: controller.lastUpdated, onRetry: controller.refresh),
            Expanded(
              child: snapshot == null
                  ? Center(
                      child: controller.error == null
                          ? const CircularProgressIndicator()
                          : const Text('No se pudo cargar la cola.'),
                    )
                  : _content(context, snapshot.withStatus(OrderStatus.listo)),
            ),
          ],
        );
      },
    );
  }

  Widget _content(BuildContext context, List<QueueItem> ready) {
    if (ready.isEmpty) {
      return const Center(child: Text('No hay pedidos listos para entregar.'));
    }
    final takeaway = [for (final i in ready) if (i.isTakeaway) i];
    final dineIn = [for (final i in ready) if (!i.isTakeaway) i];
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _section(context, 'Para llevar', takeaway),
        _section(context, 'En mesa', dineIn),
      ],
    );
  }

  Widget _section(BuildContext context, String title, List<QueueItem> items) {
    if (items.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('$title (${items.length})', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              for (final item in items)
                OrderCard(
                  item: item,
                  action: session.canDeliver
                      ? FilledButton.icon(
                          key: Key('deliver-${item.orderId}'),
                          onPressed: controller.busy.contains(item.orderId) ? null : () => _deliver(context, item),
                          icon: const Icon(Icons.shopping_bag),
                          label: const Text('Entregar'),
                        )
                      : null,
                ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _deliver(BuildContext context, QueueItem item) async {
    final result = await controller.deliver(item);
    if (context.mounted) showResult(context, result);
  }
}
