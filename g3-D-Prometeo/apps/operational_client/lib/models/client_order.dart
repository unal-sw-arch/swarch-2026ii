enum ClientPaymentMethod {
  cash('cash'),
  transfer('transfer'),
  card('card');

  const ClientPaymentMethod(this.apiValue);
  final String apiValue;

  static ClientPaymentMethod? fromApi(String? value) {
    for (final method in values) {
      if (method.apiValue == value) return method;
    }
    return null;
  }
}

class ClientMenuProduct {
  const ClientMenuProduct({
    required this.id,
    required this.name,
    required this.description,
    required this.value,
    required this.ingredients,
  });
  factory ClientMenuProduct.fromJson(
    Map<String, dynamic> json,
  ) => ClientMenuProduct(
    id: json['id'] as int,
    name: json['name'] as String,
    description: json['description'] as String?,
    value: json['value'] as int,
    ingredients: (json['ingredients'] as List? ?? const [])
        .map(
          (value) =>
              ClientProductIngredient.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
  );
  final int id;
  final String name;
  final String? description;
  final int value;
  final List<ClientProductIngredient> ingredients;
}

class ClientProductIngredient {
  const ClientProductIngredient({required this.id, required this.name});
  factory ClientProductIngredient.fromJson(Map<String, dynamic> json) =>
      ClientProductIngredient(
        id: json['id'] as int,
        name: json['name'] as String,
      );
  final int id;
  final String name;
}

class ClientMenuCategory {
  const ClientMenuCategory({
    required this.id,
    required this.name,
    required this.parentCategoryId,
    required this.isSpecial,
    required this.products,
  });
  factory ClientMenuCategory.fromJson(Map<String, dynamic> json) =>
      ClientMenuCategory(
        id: json['id'] as int,
        name: json['name'] as String,
        parentCategoryId: json['parentCategoryId'] as int?,
        isSpecial: json['isSpecial'] as bool? ?? false,
        products: (json['products'] as List)
            .map(
              (value) =>
                  ClientMenuProduct.fromJson(value as Map<String, dynamic>),
            )
            .toList(),
      );
  final int id;
  final String name;
  final int? parentCategoryId;
  final bool isSpecial;
  final List<ClientMenuProduct> products;
}

class ClientRoomMenu {
  const ClientRoomMenu({
    required this.id,
    required this.name,
    required this.isPrimary,
    required this.categories,
  });
  factory ClientRoomMenu.fromJson(Map<String, dynamic> json) => ClientRoomMenu(
    id: json['id'] as int,
    name: json['name'] as String,
    isPrimary: json['isPrimary'] as bool,
    categories: (json['categories'] as List)
        .map(
          (value) => ClientMenuCategory.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
  );
  final int id;
  final String name;
  final bool isPrimary;
  final List<ClientMenuCategory> categories;
}

class ClientOrderItem {
  const ClientOrderItem({
    required this.id,
    required this.productId,
    required this.name,
    required this.productDescription,
    required this.quantity,
    required this.deliveredQuantity,
    required this.deliveredUnitIndexes,
    required this.status,
    required this.specifications,
    required this.parentOrderItemId,
    required this.removedIngredientIds,
    required this.ingredients,
    this.categoryName = '',
    this.unitValue = 0,
  });
  factory ClientOrderItem.fromJson(
    Map<String, dynamic> json,
  ) => ClientOrderItem(
    id: json['id'] as int,
    productId: json['productId'] as int,
    name: json['name'] as String,
    categoryName: json['categoryName'] as String? ?? '',
    productDescription: json['productDescription'] as String?,
    quantity: json['quantity'] as int,
    deliveredQuantity: json['deliveredQuantity'] as int? ?? 0,
    deliveredUnitIndexes: (json['deliveredUnitIndexes'] as List? ?? const [])
        .cast<int>(),
    status: json['status'] as String? ?? 'ordered',
    specifications: json['specifications'] as String?,
    parentOrderItemId: json['parentOrderItemId'] as int?,
    removedIngredientIds: (json['removedIngredientIds'] as List).cast<int>(),
    ingredients: (json['ingredients'] as List)
        .map(
          (value) =>
              ClientProductIngredient.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
    unitValue: json['unitValue'] as int? ?? 0,
  );
  final int id;
  final int productId;
  final String name;
  final String categoryName;
  final String? productDescription;
  final int quantity;
  final int deliveredQuantity;
  final List<int> deliveredUnitIndexes;
  final String status;
  final String? specifications;
  final int? parentOrderItemId;
  final List<int> removedIngredientIds;
  final List<ClientProductIngredient> ingredients;
  final int unitValue;
}

class ClientRemovedOrderItem {
  const ClientRemovedOrderItem({
    required this.id,
    required this.name,
    required this.productDescription,
    required this.unitValue,
    required this.quantity,
    required this.specifications,
    required this.parentProductName,
    this.categoryName = '',
  });

  factory ClientRemovedOrderItem.fromJson(Map<String, dynamic> json) =>
      ClientRemovedOrderItem(
        id: json['id'] as int,
        name: json['name'] as String,
        categoryName: json['categoryName'] as String? ?? '',
        productDescription: json['productDescription'] as String?,
        unitValue: json['unitValue'] as int,
        quantity: json['quantity'] as int,
        specifications: json['specifications'] as String?,
        parentProductName: json['parentProductName'] as String?,
      );

  final int id;
  final String name;
  final String categoryName;
  final String? productDescription;
  final int unitValue;
  final int quantity;
  final String? specifications;
  final String? parentProductName;
}

class ClientSplitAllocation {
  const ClientSplitAllocation({
    required this.orderItemId,
    required this.halfUnits,
  });

  factory ClientSplitAllocation.fromJson(Map<String, dynamic> json) =>
      ClientSplitAllocation(
        orderItemId: json['orderItemId'] as int,
        halfUnits: json['halfUnits'] as int,
      );

  final int orderItemId;
  final int halfUnits;
}

class ClientSplitAccount {
  const ClientSplitAccount({
    required this.id,
    required this.name,
    required this.position,
    required this.status,
    required this.paymentMethod,
    required this.tipPercent,
    required this.tipValue,
    required this.subtotal,
    required this.totalValue,
    required this.paidAt,
    required this.allocations,
  });

  factory ClientSplitAccount.fromJson(Map<String, dynamic> json) =>
      ClientSplitAccount(
        id: json['id'] as int,
        name: json['name'] as String,
        position: json['position'] as int,
        status: json['status'] as String,
        paymentMethod: ClientPaymentMethod.fromApi(
          json['paymentMethod'] as String?,
        ),
        tipPercent: json['tipPercent'] as int? ?? 0,
        tipValue: json['tipValue'] as int? ?? 0,
        subtotal: json['subtotal'] as int? ?? 0,
        totalValue: json['totalValue'] as int? ?? 0,
        paidAt: json['paidAt'] as String?,
        allocations: (json['allocations'] as List? ?? const [])
            .map(
              (value) =>
                  ClientSplitAllocation.fromJson(value as Map<String, dynamic>),
            )
            .toList(),
      );

  final int id;
  final String name;
  final int position;
  final String status;
  final ClientPaymentMethod? paymentMethod;
  final int tipPercent;
  final int tipValue;
  final int subtotal;
  final int totalValue;
  final String? paidAt;
  final List<ClientSplitAllocation> allocations;
  bool get isPaid => status == 'paid';
}

class SplitAccountWrite {
  const SplitAccountWrite({
    required this.name,
    required this.position,
    required this.tipPercent,
    required this.customTip,
    required this.allocations,
  });

  final String name;
  final int position;
  final int tipPercent;
  final int customTip;
  final Map<int, int> allocations;

  Map<String, Object?> toJson() => {
    'name': name,
    'position': position,
    'tipPercent': tipPercent,
    'customTip': customTip,
    'allocations': [
      for (final entry in allocations.entries)
        {'orderItemId': entry.key, 'halfUnits': entry.value},
    ],
  };
}

class ClientOrder {
  const ClientOrder({
    required this.id,
    required this.tableId,
    required this.tableGroupId,
    required this.status,
    required this.description,
    required this.items,
    this.externalName,
    this.tableLabel = '',
    this.total = 0,
    this.createdAt,
    this.removedItems = const [],
    this.paymentMethod,
    this.splitAccounts = const [],
  });
  factory ClientOrder.fromJson(Map<String, dynamic> json) => ClientOrder(
    id: json['id'] as int,
    tableId: json['tableId'] as int? ?? 0,
    tableGroupId: json['tableGroupId'] as int?,
    externalName: json['externalName'] as String?,
    status: json['status'] as String,
    description: json['description'] as String?,
    items: (json['items'] as List)
        .map((value) => ClientOrderItem.fromJson(value as Map<String, dynamic>))
        .toList(),
    tableLabel: json['tableLabel'] as String? ?? '',
    total: json['total'] as int? ?? 0,
    createdAt: json['createdAt'] as String?,
    paymentMethod: ClientPaymentMethod.fromApi(
      json['paymentMethod'] as String?,
    ),
    removedItems: (json['removedItems'] as List? ?? const [])
        .map(
          (value) =>
              ClientRemovedOrderItem.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
    splitAccounts: (json['splitAccounts'] as List? ?? const [])
        .map(
          (value) => ClientSplitAccount.fromJson(value as Map<String, dynamic>),
        )
        .toList(),
  );
  final int id;
  final int tableId;
  final int? tableGroupId;
  final String? externalName;
  final String status;
  final String? description;
  final List<ClientOrderItem> items;
  final String tableLabel;
  final int total;
  final String? createdAt;
  final List<ClientRemovedOrderItem> removedItems;
  final ClientPaymentMethod? paymentMethod;
  final List<ClientSplitAccount> splitAccounts;
  bool get isExternal => externalName != null;
}

class OrderItemWrite {
  const OrderItemWrite({
    required this.productId,
    required this.quantity,
    required this.specifications,
    required this.removedIngredientIds,
    this.parentIndex,
  });
  final int productId;
  final int quantity;
  final String specifications;
  final List<int> removedIngredientIds;
  final int? parentIndex;
  Map<String, Object?> toJson() => {
    'productId': productId,
    'quantity': quantity,
    'specifications': specifications,
    'removedIngredientIds': removedIngredientIds,
    'parentIndex': parentIndex,
  };
}
