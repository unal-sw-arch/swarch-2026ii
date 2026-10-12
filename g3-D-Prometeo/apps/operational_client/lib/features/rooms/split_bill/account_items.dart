// ignore_for_file: invalid_use_of_protected_member

part of '../split_bill_page.dart';

extension _SplitAccountItems on _SplitBillPageState {
  Widget _accountTabs() => SizedBox(
    height: 76,
    child: ListView.separated(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      itemCount: _accounts.length + 1,
      separatorBuilder: (_, _) => const SizedBox(width: 9),
      itemBuilder: (_, index) {
        if (index == _accounts.length) {
          return OutlinedButton.icon(
            key: const ValueKey('add-split-account'),
            onPressed: _saving || _accounts.length >= 12 ? null : _addAccount,
            icon: const Icon(Icons.add),
            label: Text(_es ? 'Añadir' : 'Add'),
          );
        }
        final account = _accounts[index];
        return ChoiceChip(
          key: ValueKey('split-account-${account.position}'),
          selected: index == _selected,
          avatar: Icon(
            account.paid ? Icons.check_circle : Icons.circle,
            size: 13,
            color: account.paid ? Colors.green : const Color(0xFF90A4B8),
          ),
          label: Text(account.name),
          onSelected: (_) => setState(() => _selected = index),
        );
      },
    ),
  );

  Widget _billProgress() {
    final paid = _order.splitAccounts
        .where((account) => account.isPaid)
        .fold<int>(0, (sum, account) => sum + account.subtotal);
    final assigned = _order.items.fold<int>(0, (sum, item) {
      final halves = _accounts.fold<int>(
        0,
        (used, account) => used + (account.allocations[item.id] ?? 0),
      );
      return sum + item.unitValue * halves ~/ 2;
    });
    final remaining = (_order.total - assigned).clamp(0, _order.total);
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
      child: Wrap(
        spacing: 16,
        runSpacing: 4,
        children: [
          Text('${_es ? 'Mesa' : 'Table'}: ${formatPesos(_order.total)}'),
          Text(
            '${_es ? 'Pagado' : 'Paid'}: ${formatPesos(paid)}',
            style: const TextStyle(color: _SplitBillPageState._accent),
          ),
          Text(
            '${_es ? 'Sin asignar' : 'Unassigned'}: ${formatPesos(remaining)}',
          ),
        ],
      ),
    );
  }

  Widget _itemPane() => Column(
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(18, 13, 18, 9),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              TextButton(
                onPressed: _selectAll,
                child: Text(_es ? 'Seleccionar todo' : 'Select all'),
              ),
              const SizedBox(width: 8),
              TextButton(
                onPressed: _clearActive,
                child: Text(_es ? 'Desmarcar' : 'Clear'),
              ),
              const SizedBox(width: 8),
              FilledButton.tonal(
                onPressed: _divideEqually,
                child: Text(
                  _es ? 'Dividir en partes iguales' : 'Split equally',
                ),
              ),
            ],
          ),
        ),
      ),
      Expanded(
        child: ListView.separated(
          padding: const EdgeInsets.fromLTRB(18, 4, 18, 22),
          itemCount: _order.items.length,
          separatorBuilder: (_, _) => const SizedBox(height: 9),
          itemBuilder: (_, index) => _itemCard(_order.items[index]),
        ),
      ),
    ],
  );

  Widget _itemCard(ClientOrderItem item) {
    final halves = _active.allocations[item.id] ?? 0;
    final assigned = _accounts.fold(
      0,
      (sum, account) => sum + (account.allocations[item.id] ?? 0),
    );
    final selected = halves > 0;
    return Card(
      margin: EdgeInsets.only(left: item.parentOrderItemId == null ? 0 : 24),
      color: selected ? _SplitBillPageState._accentSoft : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: selected
              ? _SplitBillPageState._accent
              : _SplitBillPageState._border,
          width: selected ? 1.5 : 1,
        ),
      ),
      child: InkWell(
        key: ValueKey('split-item-${item.id}'),
        borderRadius: BorderRadius.circular(16),
        onTap: _active.paid ? null : () => _toggleItem(item),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              Checkbox(
                value: selected,
                onChanged: _active.paid ? null : (_) => _toggleItem(item),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.name,
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                      ),
                    ),
                    if (item.categoryName.isNotEmpty)
                      Text(
                        item.categoryName,
                        style: const TextStyle(color: Color(0xFF718096)),
                      ),
                    if (assigned > 0)
                      Text(
                        '${_halfLabel(assigned)} ${_es ? 'de' : 'of'} ${item.quantity} ${_es ? 'asignada' : 'assigned'}',
                        style: const TextStyle(
                          color: _SplitBillPageState._accent,
                          fontSize: 12,
                        ),
                      ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    formatPesos(item.unitValue * item.quantity),
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  if (!_active.paid && selected)
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          key: ValueKey('decrease-split-item-${item.id}'),
                          onPressed: () => _changeAllocation(item, -1),
                          icon: const Icon(Icons.remove, size: 18),
                        ),
                        Text(_halfLabel(halves)),
                        IconButton(
                          key: ValueKey('increase-split-item-${item.id}'),
                          onPressed: halves >= _remaining(item)
                              ? null
                              : () => _changeAllocation(item, 1),
                          icon: const Icon(Icons.add, size: 18),
                        ),
                      ],
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
