import 'package:flutter/material.dart';

import '../../../services/admin_api.dart';
import '../../../utils/money.dart';
import '../halls/room_layout_models.dart';
import 'catalog_models.dart';

part 'parts/catalog_views.dart';
part 'parts/catalog_ordering.dart';
part 'parts/page_components.dart';
part 'parts/catalog_actions.dart';
part 'parts/catalog_persistence.dart';
part 'parts/catalog_card.dart';
part 'parts/menu_dialog.dart';
part 'parts/category_dialog.dart';
part 'parts/ingredient_dialog.dart';
part 'parts/product_dialog.dart';

typedef LoadCatalog = Future<CatalogSnapshot> Function(String token);
typedef LoadMenuRooms = Future<List<RoomSummary>> Function(String token);
typedef AddMenu =
    Future<RestaurantMenu> Function({
      required String token,
      required String name,
      required List<MenuHallAssignment> hallAssignments,
    });
typedef AddIngredient =
    Future<CatalogIngredient> Function({
      required String token,
      required String name,
      required int categoryId,
      String? description,
    });
typedef AddCategory =
    Future<MenuCategory> Function({
      required String token,
      required int menuId,
      required String name,
      int? parentCategoryId,
      bool isSpecial,
    });
typedef AddProduct =
    Future<CatalogProduct> Function({
      required String token,
      required int menuId,
      required String name,
      required String description,
      required int value,
      required int categoryId,
      required List<int> ingredientIds,
      required List<int> hallIds,
    });
typedef UpdateProduct =
    Future<CatalogProduct> Function({
      required String token,
      required int productId,
      required String name,
      required String description,
      required int value,
      required List<int> ingredientIds,
      required List<int> hallIds,
    });
typedef DeactivateProduct =
    Future<void> Function({required String token, required int productId});
typedef ReorderProducts =
    Future<void> Function({
      required String token,
      required int categoryId,
      required List<int> productIds,
    });
typedef ReorderCategories =
    Future<void> Function({
      required String token,
      required int menuId,
      required int? parentCategoryId,
      required List<int> categoryIds,
    });
typedef RenameCategory =
    Future<void> Function({
      required String token,
      required int categoryId,
      required String name,
    });

class MenusPage extends StatefulWidget {
  const MenusPage({
    super.key,
    required this.spanish,
    required this.token,
    this.loadCatalog = getCatalog,
    this.loadRooms = getRooms,
    this.addMenu = createMenu,
    this.addIngredient = createIngredient,
    this.addCategory = createMenuCategory,
    this.addProduct = createMenuProduct,
    this.updateProduct = updateMenuProduct,
    this.deactivateProduct = deactivateMenuProduct,
    this.reorderProducts = reorderMenuProducts,
    this.reorderCategories = reorderMenuCategories,
    this.renameCategory = renameMenuCategory,
  });
  final bool spanish;
  final String token;
  final LoadCatalog loadCatalog;
  final LoadMenuRooms loadRooms;
  final AddMenu addMenu;
  final AddIngredient addIngredient;
  final AddCategory addCategory;
  final AddProduct addProduct;
  final UpdateProduct updateProduct;
  final DeactivateProduct deactivateProduct;
  final ReorderProducts reorderProducts;
  final ReorderCategories reorderCategories;
  final RenameCategory renameCategory;
  @override
  State<MenusPage> createState() => _MenusPageState();
}

class _MenusPageState extends State<MenusPage> {
  CatalogSnapshot _catalog = const CatalogSnapshot(menus: [], ingredients: []);
  List<RoomSummary> _rooms = [];
  int? _menuId;
  int? _categoryId;
  final Map<int, List<CatalogProduct>> _productOrderOverrides = {};
  final Set<int> _savingProductOrder = {};
  final Map<String, List<MenuCategory>> _categoryOrderOverrides = {};
  final Set<String> _savingCategoryOrder = {};
  bool _loading = true;
  String? _error;
  bool get _es => widget.spanish;
  RestaurantMenu? get _menu =>
      _catalog.menus.where((item) => item.id == _menuId).firstOrNull;
  MenuCategory? get _category =>
      _findCategory(_menu?.categories ?? const [], _categoryId);

  MenuCategory? _findCategory(List<MenuCategory> categories, int? id) {
    if (id == null) return null;
    for (final category in categories) {
      if (category.id == id) return category;
      final nested = _findCategory(category.subcategories, id);
      if (nested != null) return nested;
    }
    return null;
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final values = await Future.wait([
        widget.loadCatalog(widget.token),
        widget.loadRooms(widget.token),
      ]);
      _catalog = values[0] as CatalogSnapshot;
      _rooms = values[1] as List<RoomSummary>;
      _productOrderOverrides.clear();
      _categoryOrderOverrides.clear();
      if (_menuId != null && _menu == null) _resetNavigation();
    } on Object {
      _error = _es
          ? 'No se pudo cargar el catálogo.'
          : 'The catalog could not be loaded.';
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _resetNavigation() {
    _menuId = null;
    _categoryId = null;
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(
        child: OutlinedButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: Text(_error!),
        ),
      );
    }
    if (_category != null) return _categoryDetail(_category!);
    if (_menu != null) return _menuDetail(_menu!);
    return _overview();
  }
}
