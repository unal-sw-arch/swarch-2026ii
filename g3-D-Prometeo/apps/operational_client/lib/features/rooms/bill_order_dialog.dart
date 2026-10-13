import 'package:flutter/material.dart';

import '../../models/client_order.dart';
import '../../utils/money.dart';
import 'payment_method_switch.dart';
import 'multiple_payment_placeholder_page.dart';

class BillOrderDialog extends StatefulWidget {
  const BillOrderDialog({
    super.key,
    required this.spanish,
    required this.tableLabel,
    required this.order,
    required this.productsById,
    this.onBill,
    this.onSplit,
    this.readOnly = false,
  });

  final bool spanish;
  final String tableLabel;
  final ClientOrder order;
  final Map<int, ClientMenuProduct> productsById;
  final Future<void> Function(ClientPaymentMethod paymentMethod)? onBill;
  final Future<bool> Function()? onSplit;
  final bool readOnly;

  @override
  State<BillOrderDialog> createState() => _BillOrderDialogState();
}

class _BillOrderDialogState extends State<BillOrderDialog> {
  bool _billing = false;
  String? _submitError;
  ClientPaymentMethod _paymentMethod = ClientPaymentMethod.cash;

  bool get _es => widget.spanish;

  int _lineTotal(ClientOrderItem item) {
    final value = item.unitValue != 0
        ? item.unitValue
        : widget.productsById[item.productId]?.value ?? 0;
    return value * item.quantity;
  }

  int get _totalPesos => widget.order.items.fold<int>(
    0,
    (total, item) => total + _lineTotal(item),
  );

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    child: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760, maxHeight: 700),
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
                Expanded(child: _items()),
                const Divider(height: 1),
                _footer(),
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
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _es
                    ? 'Caja · Mesa ${widget.tableLabel}'
                    : 'Cashier · Table ${widget.tableLabel}',
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                ),
              ),
              Text(
                _es ? 'Resumen de consumo' : 'Order summary',
                style: const TextStyle(color: Color(0xFF73777C)),
              ),
            ],
          ),
        ),
        IconButton(
          key: const ValueKey('close-order-bill'),
          onPressed: _billing ? null : () => Navigator.pop(context),
          icon: const Icon(Icons.close),
        ),
      ],
    ),
  );

  Widget _items() {
    if (widget.order.items.isEmpty && widget.order.removedItems.isEmpty) {
      return Center(
        child: Text(
          _es ? 'No hay productos en este pedido.' : 'No items in this order.',
          style: const TextStyle(color: Color(0xFF7A7D82)),
        ),
      );
    }
    final rows = <Widget>[
      for (final line in _groupedActiveItems())
        _activeItem(line.item, line.quantity),
      if (widget.order.removedItems.isNotEmpty)
        Padding(
          padding: const EdgeInsets.fromLTRB(2, 12, 2, 4),
          child: Text(
            _es ? 'Productos retirados' : 'Removed products',
            style: const TextStyle(
              color: Color(0xFFAA5A56),
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      for (final line in _groupedRemovedItems())
        _removedItem(line.item, line.quantity),
    ];
    return ListView.separated(
      padding: const EdgeInsets.all(18),
      itemBuilder: (_, index) => rows[index],
      separatorBuilder: (_, _) => const SizedBox(height: 9),
      itemCount: rows.length,
    );
  }

  Widget _activeItem(ClientOrderItem item, int quantity) {
    final unit = item.unitValue != 0
        ? item.unitValue
        : widget.productsById[item.productId]?.value ?? 0;
    return Padding(
      padding: EdgeInsets.only(left: item.parentOrderItemId == null ? 0 : 22),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: item.parentOrderItemId == null
              ? Colors.white
              : const Color(0xFFF2F5F8),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFE2E0DD)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              Icon(
                item.parentOrderItemId == null
                    ? Icons.receipt_long_outlined
                    : Icons.subdirectory_arrow_right_rounded,
                size: 20,
                color: const Color(0xFF71859B),
              ),
              const SizedBox(width: 11),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.name,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 15,
                      ),
                    ),
                    if (item.categoryName.isNotEmpty)
                      Text(
                        item.categoryName,
                        style: const TextStyle(
                          color: Color(0xFF71859B),
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    if ((item.specifications ?? '').trim().isNotEmpty)
                      Text(
                        item.specifications!.trim(),
                        style: const TextStyle(color: Color(0xFF7A7D82)),
                      ),
                    if (quantity > 1)
                      Text(
                        '$quantity × ${formatPesos(unit)}',
                        style: const TextStyle(
                          color: Color(0xFF90949A),
                          fontSize: 12,
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Text(
                formatPesos(unit * quantity),
                style: const TextStyle(fontWeight: FontWeight.w800),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _removedItem(ClientRemovedOrderItem item, int quantity) =>
      DecoratedBox(
        decoration: BoxDecoration(
          color: const Color(0xFFFFF1F0),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFF0CBC8)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              const Icon(Icons.remove_circle_outline, color: Color(0xFFB56B65)),
              const SizedBox(width: 11),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.name,
                      style: const TextStyle(
                        color: Color(0xFF8D6966),
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (item.categoryName.isNotEmpty)
                      Text(
                        item.categoryName,
                        style: const TextStyle(
                          color: Color(0xFFA38380),
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    if ((item.specifications ?? '').trim().isNotEmpty)
                      Text(
                        item.specifications!.trim(),
                        style: const TextStyle(color: Color(0xFFA38380)),
                      ),
                    if (quantity > 1)
                      Text(
                        '$quantity × ${formatPesos(item.unitValue)}',
                        style: const TextStyle(color: Color(0xFFA38380)),
                      ),
                  ],
                ),
              ),
              Text(
                formatPesos(item.unitValue * quantity),
                style: const TextStyle(
                  color: Color(0xFFB56B65),
                  decoration: TextDecoration.lineThrough,
                ),
              ),
            ],
          ),
        ),
      );

  Widget _footer() => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_submitError != null) ...[
          Text(_submitError!, style: const TextStyle(color: Color(0xFFB64A4A))),
          const SizedBox(height: 8),
        ],
        if (!widget.readOnly) ...[
          if (widget.onSplit != null) ...[
            OutlinedButton.icon(
              key: const ValueKey('open-split-bill'),
              onPressed: _billing ? null : _openSplit,
              icon: const Icon(Icons.call_split_rounded),
              label: Text(_es ? 'Separar cuenta' : 'Split bill'),
            ),
            const SizedBox(height: 12),
          ],
          Row(
            children: [
              Expanded(
                child: PaymentMethodSwitch(
                  spanish: _es,
                  value: _paymentMethod,
                  onChanged: (value) => setState(() => _paymentMethod = value),
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton(
                key: const ValueKey('open-multiple-payment-modes'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(46, 46),
                  padding: EdgeInsets.zero,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(13),
                  ),
                ),
                onPressed: _billing
                    ? null
                    : () => Navigator.push<void>(
                        context,
                        MaterialPageRoute(
                          builder: (_) =>
                              MultiplePaymentPlaceholderPage(spanish: _es),
                        ),
                      ),
                child: const Icon(Icons.account_balance_wallet_outlined),
              ),
            ],
          ),
          const SizedBox(height: 13),
        ] else if (widget.order.paymentMethod != null) ...[
          Text(
            '${_es ? 'Método de pago' : 'Payment method'}: '
            '${_paymentLabel(widget.order.paymentMethod!)}',
            style: const TextStyle(
              color: Color(0xFF676B71),
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 10),
        ],
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _es ? 'Total' : 'Total',
                    style: const TextStyle(
                      color: Color(0xFF676B71),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    formatPesos(_totalPesos),
                    style: const TextStyle(
                      fontSize: 30,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 14),
            if (!widget.readOnly)
              FilledButton.icon(
                key: const ValueKey('submit-order-bill'),
                onPressed:
                    _billing ||
                        widget.order.splitAccounts.any(
                          (account) => account.isPaid,
                        )
                    ? null
                    : _submit,
                icon: _billing
                    ? const SizedBox.square(
                        dimension: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.point_of_sale_outlined),
                label: Text(_es ? 'Facturar' : 'Bill'),
              ),
          ],
        ),
      ],
    ),
  );

  Future<void> _openSplit() async {
    if (widget.onSplit == null) return;
    final completed = await widget.onSplit!();
    if (completed && mounted) Navigator.pop(context);
  }

  Future<void> _submit() async {
    setState(() {
      _billing = true;
      _submitError = null;
    });
    try {
      await widget.onBill!(_paymentMethod);
      if (mounted) Navigator.pop(context);
    } on Object {
      if (!mounted) return;
      setState(() {
        _submitError = _es
            ? 'No se pudo facturar esta mesa.'
            : 'This table could not be billed.';
      });
    } finally {
      if (mounted) setState(() => _billing = false);
    }
  }

  String _paymentLabel(ClientPaymentMethod method) => switch (method) {
    ClientPaymentMethod.cash => _es ? 'Efectivo' : 'Cash',
    ClientPaymentMethod.transfer => _es ? 'Transferencia' : 'Transfer',
    ClientPaymentMethod.card => _es ? 'Tarjeta' : 'Card',
  };

  List<_ActiveBillLine> _groupedActiveItems() {
    final grouped = <String, _ActiveBillLine>{};
    final parentIds = {
      for (final item in widget.order.items)
        if (item.parentOrderItemId != null) item.parentOrderItemId!,
    };
    for (final item in widget.order.items) {
      final removed = item.removedIngredientIds.toList()..sort();
      final signature = [
        item.productId,
        item.specifications ?? '',
        removed.join(','),
        item.parentOrderItemId ?? '',
        item.unitValue,
        if (parentIds.contains(item.id)) item.id,
      ].join('|');
      final current = grouped[signature];
      grouped[signature] = _ActiveBillLine(
        item,
        (current?.quantity ?? 0) + item.quantity,
      );
    }
    final lines = grouped.values.toList();
    final itemIds = lines.map((line) => line.item.id).toSet();
    final ordered = <_ActiveBillLine>[];
    final addedIds = <int>{};
    for (final parent in lines.where(
      (line) =>
          line.item.parentOrderItemId == null ||
          !itemIds.contains(line.item.parentOrderItemId),
    )) {
      ordered.add(parent);
      addedIds.add(parent.item.id);
      for (final addition in lines.where(
        (line) => line.item.parentOrderItemId == parent.item.id,
      )) {
        ordered.add(addition);
        addedIds.add(addition.item.id);
      }
    }
    ordered.addAll(lines.where((line) => !addedIds.contains(line.item.id)));
    return ordered;
  }

  List<_RemovedBillLine> _groupedRemovedItems() {
    final grouped = <String, _RemovedBillLine>{};
    for (final item in widget.order.removedItems) {
      final signature = [
        item.name,
        item.categoryName,
        item.productDescription ?? '',
        item.specifications ?? '',
        item.parentProductName ?? '',
        item.unitValue,
      ].join('|');
      final current = grouped[signature];
      grouped[signature] = _RemovedBillLine(
        item,
        (current?.quantity ?? 0) + item.quantity,
      );
    }
    return grouped.values.toList();
  }
}

class _ActiveBillLine {
  const _ActiveBillLine(this.item, this.quantity);
  final ClientOrderItem item;
  final int quantity;
}

class _RemovedBillLine {
  const _RemovedBillLine(this.item, this.quantity);
  final ClientRemovedOrderItem item;
  final int quantity;
}
