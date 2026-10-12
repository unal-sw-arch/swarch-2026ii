import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_operational/models/client_room.dart';
import 'package:restaurante_operational/features/rooms/live_room_controller.dart';

LiveRoomLayout _layout() => const LiveRoomLayout(
  roomId: 1,
  roomName: 'Principal',
  walls: [],
  groups: [],
  tables: [
    LiveRoomTable(
      id: 1,
      identifier: 'T-01',
      x: 100,
      y: 100,
      width: 100,
      height: 70,
      rotation: 0,
    ),
    LiveRoomTable(
      id: 2,
      identifier: 'T-02',
      x: 200,
      y: 100,
      width: 100,
      height: 70,
      rotation: 0,
    ),
    LiveRoomTable(
      id: 3,
      identifier: 'T-03',
      x: 500,
      y: 100,
      width: 100,
      height: 70,
      rotation: 0,
    ),
  ],
);

void main() {
  test('workers can logically join, move, and separate tables', () {
    final controller = LiveRoomController()..load(_layout());
    controller.toggleSelection(1);
    controller.toggleSelection(2);
    controller.groupSelected();

    expect(controller.groups.single.tableIds, containsAll([1, 2]));
    expect(controller.groups.single.identifier, 'T-01 + T-02');
    controller.beginMove(1);
    controller.moveBy(const Offset(40, 60));
    expect(controller.endMove(), isTrue);
    expect(controller.tables.firstWhere((table) => table.id == 1).x, 140);
    expect(controller.tables.firstWhere((table) => table.id == 2).x, 240);

    controller.ungroupSelected();
    expect(controller.groups, isEmpty);
  });

  test('the unified link action links or fully unlinks the selection', () {
    final controller = LiveRoomController()..load(_layout());
    controller.toggleSelection(1);
    controller.toggleSelection(2);
    controller.toggleSelectedGroup();
    expect(controller.groups.single.tableIds, containsAll([1, 2]));

    controller.toggleSelection(3);
    controller.toggleSelectedGroup();
    expect(controller.groups, isEmpty);
    expect(controller.selectedTableIds, containsAll([1, 2, 3]));

    controller.toggleSelectedGroup();
    expect(controller.groups.single.tableIds, containsAll([1, 2, 3]));
    expect(controller.groups.single.identifier, 'T-01 + T-02 + T-03');
  });

  test('a custom logical table name is preserved', () {
    final controller = LiveRoomController()..load(_layout());
    controller.toggleSelection(1);
    controller.toggleSelection(2);
    controller.toggleSelectedGroup(identifier: 'Familia Pérez');
    expect(controller.groups.single.identifier, 'Familia Pérez');
    expect(controller.snapshot().toLiveJson()['groups'], [
      {
        'id': -1,
        'identifier': 'Familia Pérez',
        'tableIds': [1, 2],
      },
    ]);
  });

  test('a collision restores the previous table position', () {
    final controller = LiveRoomController()..load(_layout());
    controller.beginMove(3);
    controller.moveBy(const Offset(-300, 0));
    expect(controller.invalidPlacement, isTrue);
    expect(controller.endMove(), isFalse);
    expect(controller.tables.firstWhere((table) => table.id == 3).x, 500);
  });

  test('snapping allows two table edges to touch without overlapping', () {
    final controller = LiveRoomController()..load(_layout());
    controller.beginMove(3);
    controller.moveBy(const Offset(-188, 0));

    expect(controller.interaction, LiveLayoutInteraction.magnetPreview);
    expect(controller.invalidPlacement, isFalse);
    expect(controller.tables.firstWhere((table) => table.id == 3).x, 300);
    expect(controller.endMove(), isTrue);
  });

  test('cancelling a pointer move does not leave an unsaved position', () {
    final controller = LiveRoomController()..load(_layout());
    controller.beginMove(3);
    controller.moveBy(const Offset(40, 30));
    controller.cancelMove();

    final table = controller.tables.firstWhere((table) => table.id == 3);
    expect(table.x, 500);
    expect(table.y, 100);
    expect(controller.dirty, isFalse);
  });

  test('content bounds include negative and rotated room objects', () {
    final controller = LiveRoomController()
      ..load(
        const LiveRoomLayout(
          roomId: 1,
          roomName: 'Wide room',
          groups: [],
          tables: [
            LiveRoomTable(
              id: 13,
              identifier: '13',
              x: -1200,
              y: -500,
              width: 100,
              height: 70,
              rotation: 0,
            ),
          ],
          walls: [
            LiveRoomWall(
              id: 7,
              x: 900,
              y: 400,
              width: 500,
              height: 16,
              rotation: 1.5707963267948966,
            ),
          ],
        ),
      );

    expect(controller.contentBounds.left, lessThanOrEqualTo(-1200));
    expect(controller.contentBounds.right, greaterThan(900));
    expect(controller.contentBounds.bottom, greaterThan(650));
  });
}
