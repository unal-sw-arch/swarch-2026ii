class CatalogIngredient {
  const CatalogIngredient({
    required this.id,
    required this.name,
    required this.categoryId,
    this.description,
  });
  factory CatalogIngredient.fromJson(Map<String, dynamic> json) =>
      CatalogIngredient(
        id: json['id'] as int,
        name: json['name'] as String,
        categoryId: json['categoryId'] as int,
        description: json['description'] as String?,
      );
  final int id;
  final String name;
  final int categoryId;
  final String? description;
}

class IngredientCategory {
  const IngredientCategory({
    required this.id,
    required this.name,
    required this.ingredients,
  });
  factory IngredientCategory.fromJson(Map<String, dynamic> json) =>
      IngredientCategory(
        id: json['id'] as int,
        name: json['name'] as String,
        ingredients: (json['ingredients'] as List)
            .map(
              (value) =>
                  CatalogIngredient.fromJson(value as Map<String, dynamic>),
            )
            .toList(),
      );
  final int id;
  final String name;
  final List<CatalogIngredient> ingredients;
}

class CatalogProduct {
  const CatalogProduct({
    required this.id,
    required this.name,
    required this.value,
    required this.menuId,
    required this.categoryId,
    required this.ingredientIds,
    required this.hallIds,
    this.description,
    this.isActive = true,
  });
  factory CatalogProduct.fromJson(Map<String, dynamic> json) => CatalogProduct(
    id: json['id'] as int,
    name: json['name'] as String,
    description: json['description'] as String?,
    value: json['value'] as int,
    menuId: json['menuId'] as int,
    categoryId: json['categoryId'] as int,
    ingredientIds: (json['ingredientIds'] as List).cast<int>(),
    hallIds: (json['hallIds'] as List).cast<int>(),
    isActive: json['isActive'] as bool? ?? true,
  );
  final int id;
  final String name;
  final String? description;
  final int value;
  final int menuId;
  final int categoryId;
  final List<int> ingredientIds;
  final List<int> hallIds;
  final bool isActive;
}

class MenuCategory {
  const MenuCategory({
    required this.id,
    required this.menuId,
    required this.name,
    required this.products,
    this.parentCategoryId,
    this.isSpecial = false,
    this.subcategories = const [],
  });
  factory MenuCategory.fromJson(Map<String, dynamic> json) => MenuCategory(
    id: json['id'] as int,
    menuId: json['menuId'] as int,
    name: json['name'] as String,
    parentCategoryId: json['parentCategoryId'] as int?,
    isSpecial: json['isSpecial'] as bool? ?? false,
    products: (json['products'] as List)
        .map((value) => CatalogProduct.fromJson(value as Map<String, dynamic>))
        .toList(),
    subcategories: (json['subcategories'] as List? ?? const [])
        .map((value) => MenuCategory.fromJson(value as Map<String, dynamic>))
        .toList(),
  );
  final int id;
  final int menuId;
  final String name;
  final int? parentCategoryId;
  final bool isSpecial;
  final List<CatalogProduct> products;
  final List<MenuCategory> subcategories;

  int get recursiveProductCount =>
      products.length +
      subcategories.fold(0, (sum, child) => sum + child.recursiveProductCount);
}

class MenuHallAssignment {
  const MenuHallAssignment({required this.hallId, required this.isPrimary});
  factory MenuHallAssignment.fromJson(Map<String, dynamic> json) =>
      MenuHallAssignment(
        hallId: json['hallId'] as int,
        isPrimary: json['isPrimary'] as bool,
      );
  final int hallId;
  final bool isPrimary;
}

class SecondaryRoomMenuSelection {
  const SecondaryRoomMenuSelection({
    required this.menuId,
    required this.productIds,
  });

  final int menuId;
  final List<int> productIds;
}

class RoomMenuConfiguration {
  const RoomMenuConfiguration({
    required this.primaryMenuId,
    required this.secondaryMenus,
  });

  final int primaryMenuId;
  final List<SecondaryRoomMenuSelection> secondaryMenus;
}

class RestaurantMenu {
  const RestaurantMenu({
    required this.id,
    required this.name,
    required this.hallAssignments,
    required this.categories,
  });
  factory RestaurantMenu.fromJson(Map<String, dynamic> json) => RestaurantMenu(
    id: json['id'] as int,
    name: json['name'] as String,
    hallAssignments: (json['hallAssignments'] as List)
        .map(
          (value) => MenuHallAssignment.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
    categories: (json['categories'] as List)
        .map((value) => MenuCategory.fromJson(value as Map<String, dynamic>))
        .toList(),
  );
  final int id;
  final String name;
  final List<MenuHallAssignment> hallAssignments;
  final List<MenuCategory> categories;
  List<int> get hallIds => hallAssignments.map((item) => item.hallId).toList();
  List<int> get primaryHallIds => hallAssignments
      .where((item) => item.isPrimary)
      .map((item) => item.hallId)
      .toList();
  List<int> get secondaryHallIds => hallAssignments
      .where((item) => !item.isPrimary)
      .map((item) => item.hallId)
      .toList();
  int get productCount =>
      categories.fold(0, (sum, item) => sum + item.recursiveProductCount);

  List<CatalogProduct> get products => [
    for (final category in categories) ..._categoryProducts(category),
  ];

  static Iterable<CatalogProduct> _categoryProducts(
    MenuCategory category,
  ) sync* {
    yield* category.products;
    for (final child in category.subcategories) {
      yield* _categoryProducts(child);
    }
  }
}

class CatalogSnapshot {
  const CatalogSnapshot({
    required this.menus,
    required this.ingredients,
    this.ingredientCategories = const [],
  });
  factory CatalogSnapshot.fromJson(
    Map<String, dynamic> json,
  ) => CatalogSnapshot(
    menus: (json['menus'] as List)
        .map((value) => RestaurantMenu.fromJson(value as Map<String, dynamic>))
        .toList(),
    ingredients: (json['ingredients'] as List)
        .map(
          (value) => CatalogIngredient.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
    ingredientCategories: (json['ingredientCategories'] as List? ?? const [])
        .map(
          (value) => IngredientCategory.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
  );
  final List<RestaurantMenu> menus;
  final List<CatalogIngredient> ingredients;
  final List<IngredientCategory> ingredientCategories;
}
