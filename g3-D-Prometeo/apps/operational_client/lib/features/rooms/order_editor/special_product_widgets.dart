part of '../order_editor_dialog.dart';

class _SpecialProductsPanel extends StatelessWidget {
  const _SpecialProductsPanel({
    super.key,
    required this.category,
    required this.categories,
    required this.spanish,
    required this.quantities,
    required this.onChanged,
    required this.onCategorySelected,
  });

  final ClientMenuCategory category;
  final List<ClientMenuCategory> categories;
  final bool spanish;
  final Map<int, int> quantities;
  final void Function(ClientMenuProduct product, int delta) onChanged;
  final ValueChanged<int> onCategorySelected;

  @override
  Widget build(BuildContext context) {
    final children = categories
        .where((candidate) => candidate.parentCategoryId == category.id)
        .toList();
    final products = category.products;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFDCE2E7)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0F000000),
            blurRadius: 12,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: products.isEmpty && children.isEmpty
          ? const Padding(
              padding: EdgeInsets.all(8),
              child: Text(
                'No hay productos en esta categoría.',
                style: TextStyle(color: Color(0xFF7A7D82)),
              ),
            )
          : Column(
              children: [
                if (category.parentCategoryId != null)
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      key: ValueKey('special-parent-${category.id}'),
                      onPressed: () =>
                          onCategorySelected(category.parentCategoryId!),
                      icon: const Icon(Icons.arrow_back_rounded),
                      label: Text(spanish ? 'Volver' : 'Back'),
                    ),
                  ),
                for (final child in children)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: OutlinedButton.icon(
                      key: ValueKey('special-subcategory-${child.id}'),
                      onPressed: () => onCategorySelected(child.id),
                      icon: const Icon(Icons.account_tree_outlined),
                      label: Text(child.name),
                    ),
                  ),
                for (var index = 0; index < products.length; index++) ...[
                  if (index > 0) const Divider(height: 1),
                  _SpecialProductRow(
                    product: products[index],
                    quantity: quantities[products[index].id] ?? 0,
                    onChanged: (delta) => onChanged(products[index], delta),
                  ),
                ],
              ],
            ),
    );
  }
}

class _SpecialSearchResults extends StatelessWidget {
  const _SpecialSearchResults({
    required this.results,
    required this.quantities,
    required this.spanish,
    required this.onChanged,
  });

  final List<({ClientMenuCategory category, ClientMenuProduct product})>
  results;
  final Map<int, int> quantities;
  final bool spanish;
  final void Function(ClientMenuProduct product, int delta) onChanged;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(10),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
      border: Border.all(color: const Color(0xFFDCE2E7)),
    ),
    child: results.isEmpty
        ? Padding(
            padding: const EdgeInsets.all(10),
            child: Text(
              spanish
                  ? 'No se encontraron productos especiales.'
                  : 'No special products found.',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFF7A7D82)),
            ),
          )
        : Column(
            children: [
              for (var index = 0; index < results.length; index++) ...[
                if (index > 0) const Divider(height: 1),
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    results[index].category.name,
                    style: const TextStyle(
                      color: Color(0xFF71859B),
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                _SpecialProductRow(
                  product: results[index].product,
                  quantity: quantities[results[index].product.id] ?? 0,
                  onChanged: (delta) =>
                      onChanged(results[index].product, delta),
                ),
              ],
            ],
          ),
  );
}

class _SpecialProductRow extends StatelessWidget {
  const _SpecialProductRow({
    required this.product,
    required this.quantity,
    required this.onChanged,
  });

  final ClientMenuProduct product;
  final int quantity;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          product.name,
          textAlign: TextAlign.center,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 2),
        Text(
          formatPesos(product.value),
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Color(0xFF71859B),
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 5),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            IconButton(
              key: ValueKey('decrease-special-product-${product.id}'),
              visualDensity: VisualDensity.compact,
              onPressed: quantity == 0 ? null : () => onChanged(-1),
              icon: const Icon(Icons.remove_circle_outline),
            ),
            SizedBox(
              width: 32,
              child: Text('$quantity', textAlign: TextAlign.center),
            ),
            IconButton(
              key: ValueKey('increase-special-product-${product.id}'),
              visualDensity: VisualDensity.compact,
              onPressed: () => onChanged(1),
              icon: const Icon(Icons.add_circle_outline),
            ),
          ],
        ),
      ],
    ),
  );
}
