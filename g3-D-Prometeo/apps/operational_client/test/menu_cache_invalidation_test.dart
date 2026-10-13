import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/models/client_room.dart';
import 'package:restaurante_operational/models/client_user.dart';
import 'package:restaurante_operational/services/client_realtime.dart';
import 'package:restaurante_operational/features/rooms/live_room_page.dart';

class _FakeRealtime extends ClientRealtimeService {
  _FakeRealtime(super.session);

  final _controller = StreamController<ClientRealtimeEvent>.broadcast();

  @override
  Stream<ClientRealtimeEvent> get events => _controller.stream;

  @override
  void subscribe(int roomId) {}

  @override
  void unsubscribe(int roomId) {}

  void emit(ClientRealtimeEvent event) => _controller.add(event);

  Future<void> close() async {
    await _controller.close();
    await super.dispose();
  }
}

void main() {
  testWidgets('reconnect reloads menus only when catalog changed', (
    tester,
  ) async {
    final session = ClientSession(
      token: 'test-token',
      expiresAt: DateTime.utc(2030),
      user: const ClientUser(
        id: 7,
        fullName: 'Mesero',
        username: 'mesero',
        role: 'waiter',
      ),
    );
    final realtime = _FakeRealtime(session);
    var menuLoads = 0;
    var orderLoads = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: LiveRoomPage(
          session: session,
          room: const ClientRoomSummary(id: 3, name: 'Salón', tableCount: 0),
          spanish: true,
          realtime: realtime,
          loadLayout: ({required session, required roomId}) async =>
              const LiveRoomLayout(
                roomId: 3,
                roomName: 'Salón',
                groups: [],
                walls: [],
                tables: [],
              ),
          saveLayout:
              ({required session, required roomId, required layout}) async =>
                  layout,
          loadOrders: ({required session, required roomId}) async {
            orderLoads++;
            return const [];
          },
          loadMenus: ({required session, required roomId}) async {
            menuLoads++;
            return const [];
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(menuLoads, 1);

    realtime.emit(
      const ClientRealtimeEvent(
        type: 'realtime.resync-required',
        roomId: 3,
        staleResources: {'hall:3:orders'},
      ),
    );
    await tester.pumpAndSettle();
    expect(orderLoads, greaterThan(1));
    expect(menuLoads, 1);

    realtime.emit(
      const ClientRealtimeEvent(
        type: 'realtime.resync-required',
        roomId: 3,
        staleResources: {'catalog'},
      ),
    );
    await tester.pump(const Duration(milliseconds: 250));
    await tester.pumpAndSettle();
    expect(menuLoads, 2);

    for (var index = 0; index < 3; index++) {
      realtime.emit(
        ClientRealtimeEvent(
          type: 'catalog.product.changed',
          roomId: 3,
          resource: 'catalog',
          version: index + 2,
        ),
      );
    }
    await tester.pump(const Duration(milliseconds: 250));
    await tester.pumpAndSettle();
    expect(menuLoads, 3);

    await tester.pumpWidget(const SizedBox());
    await realtime.close();
  });
}
