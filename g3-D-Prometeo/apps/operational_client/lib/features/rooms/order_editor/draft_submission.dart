// ignore_for_file: invalid_use_of_protected_member

part of '../order_editor_dialog.dart';

extension _OrderEditorDraftSubmission on _OrderEditorDialogState {
  String _defaultKey(int productId) => 'default:$productId';

  void _mergeIdenticalLines() {
    final parentKeys = {
      for (final line in _lines.values)
        if (line.parentLineKey != null) line.parentLineKey!,
    };
    final firstBySignature = <String, String>{};
    final duplicates = <String>[];
    final entries = _lines.entries.toList()
      ..sort((left, right) {
        final leftDefault = left.key.startsWith('default:') ? 0 : 1;
        final rightDefault = right.key.startsWith('default:') ? 0 : 1;
        return leftDefault.compareTo(rightDefault);
      });
    for (final entry in entries) {
      if (parentKeys.contains(entry.key)) continue;
      final line = entry.value;
      final removed = line.removedIngredientIds.toList()..sort();
      final signature = [
        line.product.id,
        line.specifications,
        removed.join(','),
        line.parentLineKey ?? '',
      ].join('|');
      final firstKey = firstBySignature[signature];
      if (firstKey == null) {
        firstBySignature[signature] = entry.key;
        continue;
      }
      final first = _lines[firstKey]!;
      first.quantity += line.quantity;
      first.deliveredQuantity += line.deliveredQuantity;
      duplicates.add(entry.key);
    }
    for (final key in duplicates) {
      _lines.remove(key);
      _expandedLineKeys.remove(key);
    }
  }

  void _remember() => _undo.add({
    for (final entry in _lines.entries) entry.key: entry.value.copy(),
  });

  void _undoLast() {
    if (_undo.isEmpty) return;
    setState(() {
      _lines
        ..clear()
        ..addAll(_undo.removeLast());
    });
  }

  bool _isSpecialProduct(int productId) => _categories
      .where((category) => category.isSpecial)
      .any((category) => category.products.any((item) => item.id == productId));

  Widget _footer() => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_submitError != null) ...[
          Text(_submitError!, style: const TextStyle(color: Color(0xFFB64A4A))),
          const SizedBox(height: 8),
        ],
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _orderDescription,
                decoration: InputDecoration(
                  labelText: _es
                      ? 'Nota general del pedido'
                      : 'General order note',
                ),
              ),
            ),
            const SizedBox(width: 14),
            FilledButton.icon(
              key: const ValueKey('submit-order'),
              onPressed:
                  _saving ||
                      (widget.existingOrder == null &&
                          !_lines.values.any((line) => line.quantity > 0))
                  ? null
                  : _submit,
              style:
                  widget.existingOrder != null &&
                      !_lines.values.any((line) => line.quantity > 0)
                  ? FilledButton.styleFrom(
                      backgroundColor: const Color(0xFFB64A4A),
                    )
                  : null,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(
                      widget.existingOrder != null &&
                              !_lines.values.any((line) => line.quantity > 0)
                          ? Icons.delete_outline
                          : Icons.send_outlined,
                    ),
              label: Text(
                widget.existingOrder != null &&
                        !_lines.values.any((line) => line.quantity > 0)
                    ? (_es ? 'Eliminar pedido' : 'Delete order')
                    : (_es ? 'Enviar' : 'Send'),
              ),
            ),
          ],
        ),
      ],
    ),
  );

  Future<void> _submit() async {
    _mergeIdenticalLines();
    setState(() {
      _saving = true;
      _submitError = null;
    });
    final ordered = _orderedSelectedEntries();
    final indexByLineKey = {
      for (var index = 0; index < ordered.length; index++)
        ordered[index].key: index,
    };
    try {
      await widget.onSubmit(_orderDescription.text.trim(), [
        for (final entry in ordered)
          OrderItemWrite(
            productId: entry.value.product.id,
            quantity: entry.value.quantity,
            specifications: entry.value.specifications,
            removedIngredientIds: entry.value.removedIngredientIds.toList(),
            parentIndex: entry.value.parentLineKey == null
                ? null
                : indexByLineKey[entry.value.parentLineKey],
          ),
      ]);
      if (mounted) Navigator.pop(context, true);
    } on Object catch (error) {
      if (mounted) {
        setState(() {
          final code = error is ClientRoomsException ? error.code : null;
          _submitError = code == 'DELIVERED_ITEM_CANNOT_BE_REMOVED'
              ? (_es
                    ? 'No se pueden retirar productos que ya fueron entregados.'
                    : 'Delivered products cannot be removed.')
              : code == 'ORDER_ITEMS_REQUIRED'
              ? (_es
                    ? 'El servidor está desactualizado y todavía rechaza pedidos vacíos.'
                    : 'The server is outdated and still rejects empty orders.')
              : (_es
                    ? 'No se pudo guardar el pedido${code == null ? '.' : ' ($code).'}'
                    : 'The order could not be saved${code == null ? '.' : ' ($code).'}');
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  List<MapEntry<String, _DraftLine>> _orderedSelectedEntries() {
    final selected = _lines.entries
        .where((entry) => entry.value.quantity > 0)
        .toList();
    final selectedKeys = selected.map((entry) => entry.key).toSet();
    final ordered = <MapEntry<String, _DraftLine>>[];
    final added = <String>{};
    for (final parent in selected.where(
      (entry) =>
          entry.value.parentLineKey == null ||
          !selectedKeys.contains(entry.value.parentLineKey),
    )) {
      ordered.add(parent);
      added.add(parent.key);
      for (final addition in selected.where(
        (entry) => entry.value.parentLineKey == parent.key,
      )) {
        ordered.add(addition);
        added.add(addition.key);
      }
    }
    ordered.addAll(selected.where((entry) => !added.contains(entry.key)));
    return ordered;
  }
}
