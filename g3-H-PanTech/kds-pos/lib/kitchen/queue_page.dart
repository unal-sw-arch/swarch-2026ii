import 'package:flutter/material.dart';

import '../auth/session_store.dart';
import '../models/queue_item.dart';
import '../widgets/status_banner.dart';
import 'order_card.dart';
import 'queue_controller.dart';
import 'status_buttons.dart';

class QueuePage extends StatelessWidget {
  const QueuePage({super.key, required this.controller, required this.session});

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
                  : _content(context, snapshot),
            ),
          ],
        );
      },
    );
  }

  Widget _content(BuildContext context, QueueSnapshot snapshot) {
    final cooking = snapshot.withStatus(OrderStatus.enPreparacion);
    final pending = snapshot.withStatus(OrderStatus.pendiente);
    final ready = snapshot.withStatus(OrderStatus.listo);
    final cap = snapshot.capacity;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            Chip(
              key: const Key('capacity'),
              avatar: Icon(cap.isFull ? Icons.warning_amber : Icons.kitchen, size: 18),
              label: Text('En preparación ${cap.inPreparation}/${cap.max}${cap.isFull ? ' · cocina llena' : ''}'),
              backgroundColor: cap.isFull ? Colors.orange.shade100 : null,
            ),
            const SizedBox(width: 12),
            Text('${pending.length} pendientes · ${ready.length} listos'),
          ],
        ),
        _section(context, 'En preparación', cooking),
        _section(context, 'Pendientes (por prioridad)', pending),
        _section(context, 'Listos para despachar', ready),
        if (snapshot.items.isEmpty) const Padding(padding: EdgeInsets.all(32), child: Center(child: Text('No hay pedidos en la cola.'))),
      ],
    );
  }

  Widget _section(BuildContext context, String title, List<QueueItem> items) {
    if (items.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('$title (${items.length})', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [for (final item in items) OrderCard(item: item, action: _actions(context, item))],
          ),
        ],
      ),
    );
  }

  Widget? _actions(BuildContext context, QueueItem item) {
    if (!session.canUpdate || item.status == OrderStatus.listo) return null;
    return StatusButtons(
      item: item,
      busy: controller.busy.contains(item.orderId),
      onStart: () => _run(context, controller.changeStatus(item, OrderStatus.enPreparacion)),
      onReady: () => _run(context, controller.changeStatus(item, OrderStatus.listo)),
    );
  }

  Future<void> _run(BuildContext context, Future<ActionResult> action) async {
    final result = await action;
    if (context.mounted) showResult(context, result);
  }
}
