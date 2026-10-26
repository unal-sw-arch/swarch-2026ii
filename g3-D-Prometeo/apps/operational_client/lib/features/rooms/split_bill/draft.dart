part of '../split_bill_page.dart';

class _SplitDraft {
  _SplitDraft({
    this.id,
    required this.name,
    required this.position,
    this.paid = false,
    this.method = ClientPaymentMethod.cash,
    this.tipPercent = 0,
    this.customTip = 0,
    this.paidSubtotal = 0,
    this.paidTip = 0,
    Map<int, int>? allocations,
  }) : allocations = allocations ?? {};

  final int? id;
  final String name;
  final int position;
  final bool paid;
  ClientPaymentMethod method;
  int tipPercent;
  int customTip;
  final int paidSubtotal;
  final int paidTip;
  final Map<int, int> allocations;
}
