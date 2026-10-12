part of '../order_editor_dialog.dart';

class _DraftLine {
  _DraftLine({
    required this.product,
    this.quantity = 0,
    this.deliveredQuantity = 0,
    this.specifications = '',
    Set<int>? removedIngredientIds,
    this.parentLineKey,
  }) : removedIngredientIds = removedIngredientIds ?? {};
  final ClientMenuProduct product;
  int quantity;
  int deliveredQuantity;
  String specifications;
  Set<int> removedIngredientIds;
  String? parentLineKey;

  _DraftLine copy() => _DraftLine(
    product: product,
    quantity: quantity,
    deliveredQuantity: deliveredQuantity,
    specifications: specifications,
    removedIngredientIds: {...removedIngredientIds},
    parentLineKey: parentLineKey,
  );
}

class _ProductSearchResult {
  const _ProductSearchResult({
    required this.product,
    required this.categoryLabel,
    required this.score,
  });

  final ClientMenuProduct product;
  final String categoryLabel;
  final int score;
}
