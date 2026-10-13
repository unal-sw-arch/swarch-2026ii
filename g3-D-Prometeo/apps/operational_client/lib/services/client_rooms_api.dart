import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../models/client_room.dart';
import '../models/client_user.dart';
import '../models/client_order.dart';
import 'cloud_api.dart';

class ClientRoomsException implements Exception {
  const ClientRoomsException(this.code);
  final String code;
}

Future<List<ClientRoomSummary>> getAssignedRooms(ClientSession session) async {
  final body = await _requestJson(
    session: session,
    method: 'GET',
    path: '/api/v1/halls',
  );
  if (body['halls'] is! List) {
    throw const ClientRoomsException('INVALID_RESPONSE');
  }
  return (body['halls'] as List)
      .map((value) => ClientRoomSummary.fromJson(value as Map<String, dynamic>))
      .toList();
}

Future<LiveRoomLayout> getLiveRoomLayout({
  required ClientSession session,
  required int roomId,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'GET',
    path: '/api/v1/halls/$roomId/layout',
  );
  return LiveRoomLayout.fromJson(body);
}

Future<LiveRoomLayout> saveLiveRoomLayout({
  required ClientSession session,
  required int roomId,
  required LiveRoomLayout layout,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PUT',
    path: '/api/v1/halls/$roomId/live-layout',
    payload: layout.toLiveJson(),
  );
  return LiveRoomLayout.fromJson(body);
}

Future<List<ClientRoomMenu>> getRoomMenus({
  required ClientSession session,
  required int roomId,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'GET',
    path: '/api/v1/halls/$roomId/menus',
  );
  return (body['menus'] as List)
      .map((value) => ClientRoomMenu.fromJson(value as Map<String, dynamic>))
      .toList();
}

Future<List<ClientOrder>> getRoomOrders({
  required ClientSession session,
  required int roomId,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'GET',
    path: '/api/v1/halls/$roomId/orders',
  );
  return (body['orders'] as List)
      .map((value) => ClientOrder.fromJson(value as Map<String, dynamic>))
      .toList();
}

Future<List<ClientOrder>> getTodayRoomOrders({
  required ClientSession session,
  required int roomId,
  DateTime? from,
  DateTime? to,
  ClientPaymentMethod? paymentMethod,
}) async {
  final query = <String, String>{
    if (from != null) 'from': from.toUtc().toIso8601String(),
    if (to != null) 'to': to.toUtc().toIso8601String(),
    if (paymentMethod != null) 'paymentMethod': paymentMethod.apiValue,
  };
  final suffix = query.isEmpty ? '' : '?${Uri(queryParameters: query).query}';
  final body = await _requestJson(
    session: session,
    method: 'GET',
    path: '/api/v1/halls/$roomId/orders/today$suffix',
  );
  return (body['orders'] as List)
      .map((value) => ClientOrder.fromJson(value as Map<String, dynamic>))
      .toList();
}

Future<void> saveTableOrder({
  required ClientSession session,
  required int roomId,
  required int tableId,
  int? orderId,
  required String description,
  required List<OrderItemWrite> items,
}) async {
  final creating = orderId == null;
  await _requestJson(
    session: session,
    method: creating ? 'POST' : 'PUT',
    path: creating
        ? '/api/v1/halls/$roomId/tables/$tableId/orders'
        : '/api/v1/halls/$roomId/orders/$orderId',
    payload: {
      'description': description,
      'items': items.map((item) => item.toJson()).toList(),
    },
    expectedStatus: creating ? 201 : 200,
  );
}

Future<void> saveExternalOrder({
  required ClientSession session,
  required int roomId,
  required String externalName,
  required String description,
  required List<OrderItemWrite> items,
}) async {
  await _requestJson(
    session: session,
    method: 'POST',
    path: '/api/v1/halls/$roomId/external-orders',
    payload: {
      'externalName': externalName,
      'description': description,
      'items': items.map((item) => item.toJson()).toList(),
    },
    expectedStatus: 201,
  );
}

Future<ClientOrder> markOrderEating({
  required ClientSession session,
  required int roomId,
  required int orderId,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PATCH',
    path: '/api/v1/halls/$roomId/orders/$orderId/status',
    payload: {'status': 'eating'},
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> transferTableOrder({
  required ClientSession session,
  required int roomId,
  required int orderId,
  required int tableId,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PATCH',
    path: '/api/v1/halls/$roomId/orders/$orderId/transfer',
    payload: {'tableId': tableId},
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> deliverOrderItem({
  required ClientSession session,
  required int roomId,
  required int orderId,
  required int itemId,
  required int unitIndex,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PATCH',
    path:
        '/api/v1/halls/$roomId/orders/$orderId/items/$itemId/units/$unitIndex/deliver',
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> deliverAllOrderItems({
  required ClientSession session,
  required int roomId,
  required int orderId,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PATCH',
    path: '/api/v1/halls/$roomId/orders/$orderId/deliver-all',
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> undoDeliveredOrderItem({
  required ClientSession session,
  required int roomId,
  required int orderId,
  required int itemId,
  required int unitIndex,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PATCH',
    path:
        '/api/v1/halls/$roomId/orders/$orderId/items/$itemId/units/$unitIndex/undo-delivery',
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> markOrderClosed({
  required ClientSession session,
  required int roomId,
  required int orderId,
  required ClientPaymentMethod paymentMethod,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PATCH',
    path: '/api/v1/halls/$roomId/orders/$orderId/status',
    payload: {'status': 'closed', 'paymentMethod': paymentMethod.apiValue},
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> saveOrderSplit({
  required ClientSession session,
  required int roomId,
  required int orderId,
  required List<SplitAccountWrite> accounts,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'PUT',
    path: '/api/v1/halls/$roomId/orders/$orderId/split',
    payload: {'accounts': accounts.map((account) => account.toJson()).toList()},
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<ClientOrder> payOrderSplitAccount({
  required ClientSession session,
  required int roomId,
  required int orderId,
  required int accountId,
  required ClientPaymentMethod paymentMethod,
  required int tipPercent,
  required int customTip,
}) async {
  final body = await _requestJson(
    session: session,
    method: 'POST',
    path: '/api/v1/halls/$roomId/orders/$orderId/split/$accountId/pay',
    payload: {
      'paymentMethod': paymentMethod.apiValue,
      'tipPercent': tipPercent,
      'customTip': customTip,
    },
  );
  return ClientOrder.fromJson(body['order'] as Map<String, dynamic>);
}

Future<Map<String, dynamic>> _requestJson({
  required ClientSession session,
  required String method,
  required String path,
  Map<String, dynamic>? payload,
  int expectedStatus = 200,
}) async {
  try {
    final headers = <String, String>{
      'Authorization': 'Bearer ${session.token}',
    };
    if (method != 'GET' && method != 'HEAD') {
      headers['Idempotency-Key'] = newIdempotencyKey();
    }
    if (payload != null) {
      headers['Content-Type'] = 'application/json';
    }
    final request = http.Request(method, cloudApiUri(path))
      ..headers.addAll(headers);
    if (payload != null) {
      request.body = jsonEncode(payload);
    }
    final streamed = await http.Client()
        .send(request)
        .timeout(const Duration(seconds: 12));
    final response = await http.Response.fromStream(streamed);
    Object? decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw ClientRoomsException('HTTP_${response.statusCode}');
    }
    if (response.statusCode != expectedStatus ||
        decoded is! Map<String, dynamic>) {
      final code = decoded is Map<String, dynamic> && decoded['error'] is String
          ? decoded['error'] as String
          : 'HTTP_${response.statusCode}';
      throw ClientRoomsException(code);
    }
    return decoded;
  } on ClientRoomsException {
    rethrow;
  } on Object catch (error, stackTrace) {
    debugPrint('Cloud API request failed ($method $path): $error');
    debugPrint('$stackTrace');
    throw const ClientRoomsException('CONNECTION_FAILED');
  }
}
