part of '../admin_api.dart';

Future<CatalogSnapshot> getCatalog(String token) async {
  final body = await _catalogRequest(
    token: token,
    method: 'GET',
    path: '/api/v1/catalog',
  );
  return CatalogSnapshot.fromJson(body);
}

Future<CatalogIngredient> createIngredient({
  required String token,
  required String name,
  required int categoryId,
  String? description,
}) async {
  final body = await _catalogRequest(
    token: token,
    method: 'POST',
    path: '/api/v1/admin/ingredients',
    payload: {
      'name': name,
      'description': description,
      'categoryId': categoryId,
    },
    expectedStatus: 201,
  );
  return CatalogIngredient.fromJson(body['ingredient'] as Map<String, dynamic>);
}

Future<IngredientCategory> createIngredientCategory({
  required String token,
  required String name,
}) async {
  final body = await _catalogRequest(
    token: token,
    method: 'POST',
    path: '/api/v1/admin/ingredient-categories',
    payload: {'name': name},
    expectedStatus: 201,
  );
  return IngredientCategory.fromJson(body['category'] as Map<String, dynamic>);
}

Future<RestaurantMenu> createMenu({
  required String token,
  required String name,
  required List<MenuHallAssignment> hallAssignments,
}) async {
  final body = await _catalogRequest(
    token: token,
    method: 'POST',
    path: '/api/v1/admin/menus',
    payload: {
      'name': name,
      'hallAssignments': [
        for (final assignment in hallAssignments)
          {'hallId': assignment.hallId, 'isPrimary': assignment.isPrimary},
      ],
    },
    expectedStatus: 201,
  );
  return RestaurantMenu.fromJson(body['menu'] as Map<String, dynamic>);
}

Future<void> updateRoomMenus({
  required String token,
  required int roomId,
  required RoomMenuConfiguration configuration,
}) async {
  await _catalogRequest(
    token: token,
    method: 'PUT',
    path: '/api/v1/admin/halls/$roomId/menus',
    payload: {
      'primaryMenuId': configuration.primaryMenuId,
      'secondaryMenus': [
        for (final selection in configuration.secondaryMenus)
          {'menuId': selection.menuId, 'productIds': selection.productIds},
      ],
    },
  );
}

Future<MenuCategory> createMenuCategory({
  required String token,
  required int menuId,
  required String name,
  int? parentCategoryId,
  bool isSpecial = false,
}) async {
  final body = await _catalogRequest(
    token: token,
    method: 'POST',
    path: '/api/v1/admin/menus/$menuId/categories',
    payload: {
      'name': name,
      'parentCategoryId': parentCategoryId,
      'isSpecial': isSpecial,
    },
    expectedStatus: 201,
  );
  return MenuCategory.fromJson(body['category'] as Map<String, dynamic>);
}

Future<CatalogProduct> createMenuProduct({
  required String token,
  required int menuId,
  required String name,
  required String description,
  required int value,
  required int categoryId,
  required List<int> ingredientIds,
  required List<int> hallIds,
}) async {
  final body = await _catalogRequest(
    token: token,
    method: 'POST',
    path: '/api/v1/admin/menus/$menuId/products',
    payload: {
      'name': name,
      'description': description,
      'value': value,
      'categoryId': categoryId,
      'ingredientIds': ingredientIds,
      'hallIds': hallIds,
    },
    expectedStatus: 201,
  );
  return CatalogProduct.fromJson(body['product'] as Map<String, dynamic>);
}

Future<CatalogProduct> updateMenuProduct({
  required String token,
  required int productId,
  required String name,
  required String description,
  required int value,
  required List<int> ingredientIds,
  required List<int> hallIds,
}) async {
  final body = await _catalogRequest(
    token: token,
    method: 'PATCH',
    path: '/api/v1/admin/products/$productId',
    payload: {
      'name': name,
      'description': description,
      'value': value,
      'ingredientIds': ingredientIds,
      'hallIds': hallIds,
    },
  );
  return CatalogProduct.fromJson(body['product'] as Map<String, dynamic>);
}

Future<void> deactivateMenuProduct({
  required String token,
  required int productId,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/products/$productId',
  );
  final response = await http
      .delete(endpoint, headers: _headers(token, mutation: true))
      .timeout(const Duration(seconds: 12));
  if (response.statusCode != 204) {
    throw AdminRegistrationException(
      'PRODUCT_DEACTIVATION_FAILED',
      statusCode: response.statusCode,
    );
  }
}

Future<void> reorderMenuProducts({
  required String token,
  required int categoryId,
  required List<int> productIds,
}) async {
  await _catalogRequest(
    token: token,
    method: 'PUT',
    path: '/api/v1/admin/categories/$categoryId/product-order',
    payload: {'productIds': productIds},
  );
}

Future<void> reorderMenuCategories({
  required String token,
  required int menuId,
  required int? parentCategoryId,
  required List<int> categoryIds,
}) async {
  await _catalogRequest(
    token: token,
    method: 'PUT',
    path: '/api/v1/admin/menus/$menuId/category-order',
    payload: {'parentCategoryId': parentCategoryId, 'categoryIds': categoryIds},
  );
}

Future<void> renameMenuCategory({
  required String token,
  required int categoryId,
  required String name,
}) async {
  await _catalogRequest(
    token: token,
    method: 'PATCH',
    path: '/api/v1/admin/categories/$categoryId',
    payload: {'name': name},
  );
}

Future<Map<String, dynamic>> _catalogRequest({
  required String token,
  required String method,
  required String path,
  Map<String, Object?>? payload,
  int expectedStatus = 200,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(path);
  final request = http.Request(method, endpoint)
    ..headers.addAll(_headers(token, mutation: method != 'GET'));
  if (payload != null) {
    request.headers['content-type'] = 'application/json';
    request.body = jsonEncode(payload);
  }
  final streamed = await request.send().timeout(const Duration(seconds: 12));
  final response = await http.Response.fromStream(streamed);
  Object? decoded;
  try {
    decoded = jsonDecode(response.body);
  } on FormatException {
    decoded = null;
  }
  if (response.statusCode == expectedStatus &&
      decoded is Map<String, dynamic>) {
    return decoded;
  }
  final code = decoded is Map<String, dynamic> && decoded['error'] is String
      ? decoded['error'] as String
      : 'CATALOG_REQUEST_FAILED';
  throw AdminRegistrationException(code, statusCode: response.statusCode);
}
