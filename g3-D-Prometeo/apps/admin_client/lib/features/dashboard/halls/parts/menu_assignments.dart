part of '../halls_page.dart';

class _RoomMenusDialog extends StatefulWidget {
  const _RoomMenusDialog({
    required this.spanish,
    required this.room,
    required this.menus,
  });

  final bool spanish;
  final RoomSummary room;
  final List<RestaurantMenu> menus;

  @override
  State<_RoomMenusDialog> createState() => _RoomMenusDialogState();
}

class _RoomMenusDialogState extends State<_RoomMenusDialog> {
  late int? _primaryMenuId = widget.menus
      .where((menu) => menu.primaryHallIds.contains(widget.room.id))
      .firstOrNull
      ?.id;
  late final Set<int> _secondaryMenuIds = {
    for (final menu in widget.menus)
      if (menu.secondaryHallIds.contains(widget.room.id)) menu.id,
  };
  late final Map<int, Set<int>> _secondaryProductIds = {
    for (final menu in widget.menus)
      menu.id: {
        for (final product in menu.products)
          if (product.hallIds.contains(widget.room.id)) product.id,
      },
  };

  bool get _canSave =>
      _primaryMenuId != null &&
      _secondaryMenuIds.every(
        (menuId) => _secondaryProductIds[menuId]!.isNotEmpty,
      );

  void _toggleSecondary(RestaurantMenu menu, bool selected) {
    setState(() {
      if (selected) {
        _secondaryMenuIds.add(menu.id);
      } else {
        _secondaryMenuIds.remove(menu.id);
      }
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(
      widget.spanish
          ? 'Menús de ${widget.room.name}'
          : '${widget.room.name} menus',
    ),
    content: SizedBox(
      width: 620,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              widget.spanish
                  ? 'Escoge el menú principal y, si lo necesitas, menús secundarios con sus productos permitidos.'
                  : 'Choose the primary menu and, if needed, secondary menus with their allowed products.',
              style: const TextStyle(color: Color(0xFF70757A)),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<int>(
              key: const ValueKey('room-primary-menu'),
              initialValue: _primaryMenuId,
              decoration: InputDecoration(
                labelText: widget.spanish
                    ? 'Escoger menú principal'
                    : 'Choose primary menu',
                prefixIcon: const Icon(Icons.menu_book_outlined),
              ),
              items: [
                for (final menu in widget.menus)
                  DropdownMenuItem(value: menu.id, child: Text(menu.name)),
              ],
              onChanged: (menuId) => setState(() {
                _primaryMenuId = menuId;
                if (menuId != null) _secondaryMenuIds.remove(menuId);
              }),
            ),
            const SizedBox(height: 22),
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                widget.spanish ? 'Menús secundarios' : 'Secondary menus',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            const SizedBox(height: 8),
            for (final menu in widget.menus)
              if (menu.id != _primaryMenuId)
                _SecondaryMenuOption(
                  spanish: widget.spanish,
                  roomId: widget.room.id,
                  menu: menu,
                  selected: _secondaryMenuIds.contains(menu.id),
                  selectedProductIds: _secondaryProductIds[menu.id]!,
                  onSelected: (selected) => _toggleSecondary(menu, selected),
                  onProductChanged: (productId, selected) => setState(() {
                    if (selected) {
                      _secondaryProductIds[menu.id]!.add(productId);
                    } else {
                      _secondaryProductIds[menu.id]!.remove(productId);
                    }
                  }),
                ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
      ),
      FilledButton.icon(
        key: const ValueKey('save-room-menus'),
        onPressed: !_canSave
            ? null
            : () => Navigator.pop(
                context,
                RoomMenuConfiguration(
                  primaryMenuId: _primaryMenuId!,
                  secondaryMenus: [
                    for (final menuId in _secondaryMenuIds)
                      SecondaryRoomMenuSelection(
                        menuId: menuId,
                        productIds: _secondaryProductIds[menuId]!.toList(),
                      ),
                  ],
                ),
              ),
        icon: const Icon(Icons.save_outlined),
        label: Text(widget.spanish ? 'Guardar' : 'Save'),
      ),
    ],
  );
}

class _SecondaryMenuOption extends StatelessWidget {
  const _SecondaryMenuOption({
    required this.spanish,
    required this.roomId,
    required this.menu,
    required this.selected,
    required this.selectedProductIds,
    required this.onSelected,
    required this.onProductChanged,
  });

  final bool spanish;
  final int roomId;
  final RestaurantMenu menu;
  final bool selected;
  final Set<int> selectedProductIds;
  final ValueChanged<bool> onSelected;
  final void Function(int productId, bool selected) onProductChanged;

  @override
  Widget build(BuildContext context) {
    final choices = _menuProductChoices(menu);
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Material(
        color: const Color(0xFFF7F8F9),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: Color(0xFFE2E5E8)),
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          children: [
            CheckboxListTile(
              key: ValueKey('secondary-menu-${menu.id}'),
              value: selected,
              onChanged: choices.isEmpty
                  ? null
                  : (value) => onSelected(value ?? false),
              title: Text(menu.name),
              subtitle: choices.isEmpty
                  ? Text(spanish ? 'No tiene productos' : 'No products')
                  : Text(
                      spanish
                          ? '${selectedProductIds.length} productos permitidos'
                          : '${selectedProductIds.length} allowed products',
                    ),
              controlAffinity: ListTileControlAffinity.leading,
            ),
            if (selected) ...[
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    spanish
                        ? 'Escoge los productos disponibles:'
                        : 'Choose the available products:',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ),
              for (final choice in choices)
                CheckboxListTile(
                  key: ValueKey(
                    'secondary-product-${menu.id}-${choice.product.id}',
                  ),
                  dense: true,
                  value: selectedProductIds.contains(choice.product.id),
                  onChanged: (value) =>
                      onProductChanged(choice.product.id, value ?? false),
                  title: Text(choice.product.name),
                  subtitle: Text(choice.categoryPath),
                  controlAffinity: ListTileControlAffinity.leading,
                ),
            ],
          ],
        ),
      ),
    );
  }
}

class _MenuProductChoice {
  const _MenuProductChoice(this.product, this.categoryPath);
  final CatalogProduct product;
  final String categoryPath;
}

List<_MenuProductChoice> _menuProductChoices(RestaurantMenu menu) {
  final result = <_MenuProductChoice>[];
  void visit(MenuCategory category, List<String> parents) {
    final path = [...parents, category.name];
    for (final product in category.products.where((item) => item.isActive)) {
      result.add(_MenuProductChoice(product, path.join(' › ')));
    }
    for (final child in category.subcategories) {
      visit(child, path);
    }
  }

  for (final category in menu.categories) {
    visit(category, const []);
  }
  return result;
}
