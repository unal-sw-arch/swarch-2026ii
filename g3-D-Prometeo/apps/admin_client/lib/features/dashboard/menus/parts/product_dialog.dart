part of '../menus_page.dart';

class _ProductDraft {
  const _ProductDraft(
    this.name,
    this.description,
    this.value,
    this.ingredientIds,
    this.hallIds,
  );
  final String name;
  final String description;
  final int value;
  final List<int> ingredientIds;
  final List<int> hallIds;
}

class _ProductDialog extends StatefulWidget {
  const _ProductDialog({
    required this.spanish,
    required this.ingredients,
    required this.secondaryRooms,
    this.product,
    this.onDeactivate,
  });
  final bool spanish;
  final List<CatalogIngredient> ingredients;
  final List<RoomSummary> secondaryRooms;
  final CatalogProduct? product;
  final Future<void> Function()? onDeactivate;
  @override
  State<_ProductDialog> createState() => _ProductDialogState();
}

class _ProductDialogState extends State<_ProductDialog> {
  final _name = TextEditingController();
  final _description = TextEditingController();
  final _price = TextEditingController();
  final Set<int> _ingredients = {};
  final Set<int> _halls = {};
  @override
  void initState() {
    super.initState();
    final product = widget.product;
    if (product == null) return;
    _name.text = product.name;
    _description.text = product.description ?? '';
    _price.text = product.value.toString();
    _ingredients.addAll(product.ingredientIds);
    _halls.addAll(product.hallIds);
  }

  @override
  void dispose() {
    _name.dispose();
    _description.dispose();
    _price.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      widget.product == null
          ? (widget.spanish ? 'Nuevo producto' : 'New product')
          : (widget.spanish ? 'Editar producto' : 'Edit product'),
    ),
    content: SizedBox(
      width: 520,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _name,
              autofocus: true,
              decoration: InputDecoration(
                labelText: widget.spanish ? 'Nombre' : 'Name',
              ),
            ),
            TextField(
              controller: _description,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: widget.spanish ? 'Descripción' : 'Description',
              ),
            ),
            TextField(
              controller: _price,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              decoration: InputDecoration(
                labelText: widget.spanish ? 'Precio' : 'Price',
                prefixText: '\$ ',
              ),
            ),
            const SizedBox(height: 14),
            Text(widget.spanish ? 'Ingredientes' : 'Ingredients'),
            Wrap(
              spacing: 7,
              children: [
                for (final item in widget.ingredients)
                  FilterChip(
                    label: Text(item.name),
                    selected: _ingredients.contains(item.id),
                    onSelected: (value) => setState(
                      () => value
                          ? _ingredients.add(item.id)
                          : _ingredients.remove(item.id),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              widget.spanish
                  ? 'Mostrar desde este menú secundario en:'
                  : 'Expose from this secondary menu in:',
            ),
            for (final room in widget.secondaryRooms)
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                value: _halls.contains(room.id),
                title: Text(room.name),
                onChanged: (value) => setState(
                  () => value == true
                      ? _halls.add(room.id)
                      : _halls.remove(room.id),
                ),
              ),
          ],
        ),
      ),
    ),
    actions: [
      if (widget.product != null)
        TextButton.icon(
          onPressed: widget.onDeactivate,
          icon: const Icon(Icons.delete_outline),
          label: Text(widget.spanish ? 'Desactivar' : 'Deactivate'),
          style: TextButton.styleFrom(foregroundColor: Colors.red.shade700),
        ),
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
      ),
      FilledButton(
        key: const ValueKey('submit-product'),
        onPressed: () {
          final price = int.tryParse(_price.text.replaceAll(' ', ''));
          if (_name.text.trim().length >= 2 && price != null && price >= 0) {
            Navigator.pop(
              context,
              _ProductDraft(
                _name.text.trim(),
                _description.text.trim(),
                price,
                _ingredients.toList(),
                _halls.toList(),
              ),
            );
          }
        },
        child: Text(
          widget.product == null
              ? (widget.spanish ? 'Crear' : 'Create')
              : (widget.spanish ? 'Guardar' : 'Save'),
        ),
      ),
    ],
  );
}
