import 'package:flutter/material.dart';

import '../../models/client_order.dart';
import 'centered_notice.dart';

class DeliveryOrderDialog extends StatefulWidget {
  const DeliveryOrderDialog({
    super.key,
    required this.spanish,
    required this.tableLabel,
    required this.initialOrder,
    required this.onDeliver,
    this.onDeliverAll,
    required this.onUndoDelivery,
    required this.onEditOrder,
  });

  final bool spanish;
  final String tableLabel;
  final ClientOrder initialOrder;
  final Future<ClientOrder> Function(int itemId, int unitIndex) onDeliver;
  final Future<ClientOrder> Function()? onDeliverAll;
  final Future<ClientOrder> Function(int itemId, int unitIndex) onUndoDelivery;
  final VoidCallback onEditOrder;

  @override
  State<DeliveryOrderDialog> createState() => _DeliveryOrderDialogState();
}

class _DeliveryOrderDialogState extends State<DeliveryOrderDialog> {
  late ClientOrder _order = widget.initialOrder;
  final Set<String> _delivering = {};
  final List<(int, int)> _deliveredActions = [];
  bool _deliveringAll = false;
  String? _error;

  bool get _es => widget.spanish;
  bool get _complete =>
      _order.items.isNotEmpty &&
      _order.items.every((item) => item.deliveredQuantity >= item.quantity);

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    child: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 820, maxHeight: 760),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: const Color(0xFFFAF9F6),
              borderRadius: BorderRadius.circular(22),
              boxShadow: const [
                BoxShadow(color: Color(0x44000000), blurRadius: 30),
              ],
            ),
            child: Column(
              children: [
                _header(),
                const Divider(height: 1),
                if (_complete) _completeBanner(),
                if ((_order.description ?? '').trim().isNotEmpty)
                  _generalNote(),
                if (!_complete && _order.items.isNotEmpty) _deliverAllButton(),
                Expanded(child: _items()),
              ],
            ),
          ),
        ),
      ),
    ),
  );

  Widget _header() => Padding(
    padding: const EdgeInsets.fromLTRB(22, 16, 12, 14),
    child: Row(
      children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: const Color(0xFFFFEDB0),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(
            Icons.room_service_outlined,
            color: Color(0xFF8C6A18),
          ),
        ),
        const SizedBox(width: 13),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _es
                    ? 'Entrega · Mesa ${widget.tableLabel}'
                    : 'Delivery · Table ${widget.tableLabel}',
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                _complete
                    ? (_es
                          ? 'Pedido completamente entregado'
                          : 'Order fully delivered')
                    : (_es
                          ? 'Toca cada producto cuando llegue a la mesa'
                          : 'Tap each item when it reaches the table'),
                style: const TextStyle(color: Color(0xFF73777C)),
              ),
            ],
          ),
        ),
        IconButton(
          key: const ValueKey('undo-order-delivery'),
          tooltip: _es ? 'Deshacer entrega' : 'Undo delivery',
          onPressed: _delivering.isEmpty && _deliveredActions.isNotEmpty
              ? _undoLastDelivery
              : null,
          icon: const Icon(Icons.undo_rounded),
        ),
        IconButton(
          key: const ValueKey('edit-pending-order'),
          tooltip: _es ? 'Modificar pedido' : 'Edit order',
          onPressed: _delivering.isEmpty ? widget.onEditOrder : null,
          icon: const Icon(Icons.edit_outlined),
        ),
        IconButton(
          key: const ValueKey('close-order-delivery'),
          onPressed: _delivering.isEmpty ? () => Navigator.pop(context) : null,
          icon: const Icon(Icons.close),
        ),
      ],
    ),
  );

  Widget _completeBanner() => Container(
    width: double.infinity,
    margin: const EdgeInsets.fromLTRB(18, 14, 18, 0),
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
    decoration: BoxDecoration(
      color: const Color(0xFFE4F3E1),
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: const Color(0xFFBFDDB9)),
    ),
    child: Row(
      children: [
        const Icon(Icons.check_circle, color: Color(0xFF568D50)),
        const SizedBox(width: 9),
        Expanded(
          child: Text(
            _es
                ? 'Todos los productos fueron entregados. La mesa ahora está comiendo.'
                : 'Every item was delivered. The table is now eating.',
            style: const TextStyle(
              color: Color(0xFF477842),
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    ),
  );

  Widget _generalNote() => Container(
    width: double.infinity,
    margin: const EdgeInsets.fromLTRB(18, 14, 18, 0),
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: const Color(0xFFF0F2F4),
      borderRadius: BorderRadius.circular(12),
    ),
    child: Text(
      '${_es ? 'Nota del pedido' : 'Order note'}: ${_order.description!.trim()}',
      style: const TextStyle(color: Color(0xFF555C63)),
    ),
  );

  Widget _deliverAllButton() => Padding(
    padding: const EdgeInsets.fromLTRB(18, 14, 18, 0),
    child: SizedBox(
      width: double.infinity,
      child: FilledButton.icon(
        key: const ValueKey('deliver-all-order-items'),
        onPressed: _delivering.isEmpty ? _deliverAll : null,
        icon: _deliveringAll
            ? const SizedBox.square(
                dimension: 17,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white,
                ),
              )
            : const Icon(Icons.done_all_rounded),
        label: Text(
          _deliveringAll
              ? (_es ? 'Entregando…' : 'Delivering…')
              : (_es ? 'Entregar todos' : 'Deliver all'),
        ),
      ),
    ),
  );

  Widget _items() {
    if (_order.items.isEmpty) {
      return Center(
        child: Text(
          _es ? 'Este pedido no tiene productos.' : 'This order has no items.',
          style: const TextStyle(color: Color(0xFF7A7D82)),
        ),
      );
    }
    final ids = _order.items.map((item) => item.id).toSet();
    final roots = _order.items
        .where(
          (item) =>
              item.parentOrderItemId == null ||
              !ids.contains(item.parentOrderItemId),
        )
        .toList();
    return ListView.separated(
      padding: const EdgeInsets.all(18),
      itemCount: roots.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (_, index) {
        final root = roots[index];
        final additions = _order.items
            .where((item) => item.parentOrderItemId == root.id)
            .toList();
        return _itemGroup(root, additions);
      },
    );
  }

  Widget _itemGroup(ClientOrderItem item, List<ClientOrderItem> additions) =>
      Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE0E3E6)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0D000000),
              blurRadius: 12,
              offset: Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var unit = 0; unit < item.quantity; unit++) ...[
              _unit(item, unit: unit, special: false),
              if (unit < item.quantity - 1) const SizedBox(height: 8),
            ],
            if (additions.isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 6),
                child: Row(
                  children: [
                    const Icon(
                      Icons.subdirectory_arrow_right_rounded,
                      size: 18,
                      color: Color(0xFF71859B),
                    ),
                    const SizedBox(width: 7),
                    Text(
                      _es ? 'Adiciones asociadas' : 'Linked additions',
                      style: const TextStyle(
                        color: Color(0xFF71859B),
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              for (final addition in additions)
                for (var unit = 0; unit < addition.quantity; unit++)
                  Padding(
                    padding: const EdgeInsets.only(left: 22, top: 6),
                    child: _unit(addition, unit: unit, special: true),
                  ),
            ],
          ],
        ),
      );

  Widget _unit(
    ClientOrderItem item, {
    required int unit,
    required bool special,
  }) {
    final delivered = item.deliveredUnitIndexes.contains(unit);
    final operationKey = '${item.id}:$unit';
    final busy = _delivering.contains(operationKey);
    final removed = item.ingredients
        .where(
          (ingredient) => item.removedIngredientIds.contains(ingredient.id),
        )
        .toList();
    return Material(
      color: delivered
          ? const Color(0xFFE8F3E5)
          : special
          ? const Color(0xFFF2F5F8)
          : const Color(0xFFFFFAEA),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        key: ValueKey('deliver-order-item-${item.id}-unit-$unit'),
        onTap: delivered || busy ? null : () => _deliver(item.id, unit),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(13),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 160),
                child: busy && !delivered
                    ? const SizedBox.square(
                        key: ValueKey('busy'),
                        dimension: 23,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(
                        delivered
                            ? Icons.check_circle_rounded
                            : Icons.radio_button_unchecked_rounded,
                        key: ValueKey(delivered),
                        color: delivered
                            ? const Color(0xFF5B9B66)
                            : const Color(0xFF9A7A2E),
                      ),
              ),
              const SizedBox(width: 11),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            item.name,
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w800,
                              decoration: delivered
                                  ? TextDecoration.lineThrough
                                  : null,
                            ),
                          ),
                        ),
                        if (item.quantity > 1)
                          Text(
                            '${unit + 1}/${item.quantity}',
                            style: const TextStyle(
                              color: Color(0xFF7A7D82),
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                      ],
                    ),
                    if (item.categoryName.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 3),
                        child: Text(
                          item.categoryName,
                          style: const TextStyle(
                            color: Color(0xFF71859B),
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    if ((item.productDescription ?? '').trim().isNotEmpty)
                      _detail(
                        _es ? 'Producto' : 'Product',
                        item.productDescription!.trim(),
                      ),
                    if ((item.specifications ?? '').trim().isNotEmpty)
                      _detail(
                        _es ? 'Indicaciones' : 'Instructions',
                        item.specifications!.trim(),
                      ),
                    if (removed.isNotEmpty) ...[
                      const SizedBox(height: 7),
                      Wrap(
                        spacing: 5,
                        runSpacing: 5,
                        children: [
                          for (final ingredient in removed)
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: const Color(0xFFFFE8E5),
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: Text(
                                '${_es ? 'Sin' : 'No'} ${ingredient.name}',
                                style: const TextStyle(
                                  color: Color(0xFFAB514A),
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _detail(String label, String value) => Padding(
    padding: const EdgeInsets.only(top: 5),
    child: Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: '$label: ',
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          TextSpan(text: value),
        ],
      ),
      style: const TextStyle(
        color: Color(0xFF62676C),
        fontSize: 13,
        height: 1.3,
      ),
    ),
  );

  Future<void> _deliver(int itemId, int unitIndex) async {
    if (_deliveringAll) return;
    final operationKey = '$itemId:$unitIndex';
    setState(() {
      _delivering.add(operationKey);
      _error = null;
    });
    try {
      final updated = await widget.onDeliver(itemId, unitIndex);
      if (mounted) {
        setState(() {
          _order = updated;
          _deliveredActions.add((itemId, unitIndex));
        });
      }
    } on Object {
      if (mounted) {
        setState(() {
          _error = _es
              ? 'No se pudo marcar el producto como entregado.'
              : 'The item could not be marked as delivered.';
        });
        showCenteredNotice(context, _error!);
      }
    } finally {
      if (mounted) setState(() => _delivering.remove(operationKey));
    }
  }

  Future<void> _deliverAll() async {
    if (_delivering.isNotEmpty || _complete) return;
    final pending = <(int, int)>[
      for (final item in _order.items)
        for (var unit = item.deliveredQuantity; unit < item.quantity; unit++)
          (item.id, unit),
    ];
    if (pending.isEmpty) return;
    setState(() {
      _deliveringAll = true;
      _error = null;
      _delivering.addAll(pending.map((action) => '${action.$1}:${action.$2}'));
    });
    try {
      if (widget.onDeliverAll != null) {
        final updated = await widget.onDeliverAll!();
        if (!mounted) return;
        setState(() {
          _order = updated;
          _deliveredActions.addAll(pending);
        });
        return;
      }
      for (final action in pending) {
        final updated = await widget.onDeliver(action.$1, action.$2);
        if (!mounted) return;
        setState(() {
          _order = updated;
          _deliveredActions.add(action);
        });
      }
    } on Object {
      if (mounted) {
        _error = _es
            ? 'No se pudieron entregar todos los productos.'
            : 'Not all items could be delivered.';
        showCenteredNotice(context, _error!);
      }
    } finally {
      if (mounted) {
        setState(() {
          _deliveringAll = false;
          _delivering.clear();
        });
      }
    }
  }

  Future<void> _undoLastDelivery() async {
    if (_deliveredActions.isEmpty) return;
    final action = _deliveredActions.last;
    final operationKey = '${action.$1}:${action.$2}';
    setState(() {
      _delivering.add(operationKey);
      _error = null;
    });
    try {
      final updated = await widget.onUndoDelivery(action.$1, action.$2);
      if (mounted) {
        setState(() {
          _order = updated;
          _deliveredActions.removeLast();
        });
      }
    } on Object {
      if (mounted) {
        showCenteredNotice(
          context,
          _es
              ? 'No se pudo deshacer la entrega.'
              : 'The delivery could not be undone.',
        );
      }
    } finally {
      if (mounted) setState(() => _delivering.remove(operationKey));
    }
  }
}
