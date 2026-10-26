import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:restaurante_admin/features/dashboard/halls/room_layout_controller.dart';
import 'package:restaurante_admin/features/dashboard/halls/room_layout_models.dart';

RoomLayoutModel _layout() => const RoomLayoutModel(
  roomId: 1,
  roomName: 'Main Hall',
  walls: [],
  groups: [],
  tables: [
    RoomTableModel(
      id: 1,
      identifier: '1',
      x: 100,
      y: 100,
      width: 100,
      height: 70,
    ),
    RoomTableModel(
      id: 2,
      identifier: '2',
      x: 200,
      y: 100,
      width: 100,
      height: 70,
    ),
    RoomTableModel(
      id: 3,
      identifier: 'VIP-A',
      x: 500,
      y: 100,
      width: 100,
      height: 70,
    ),
  ],
);

void main() {
  test('groups physical tables without destroying their geometry', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.toggleSelection(1);
    controller.toggleSelection(2);
    controller.groupSelected();

    expect(controller.groups, hasLength(1));
    expect(controller.groups.single.identifier, '1 + 2');
    expect(controller.groups.single.tableIds, containsAll([1, 2]));
    expect(controller.tables, hasLength(3));

    controller.beginMove(1);
    controller.moveBy(const Offset(50, 80));
    controller.endMove();
    expect(controller.tables.firstWhere((table) => table.id == 1).x, 150);
    expect(controller.tables.firstWhere((table) => table.id == 2).x, 250);
  });

  test('rejects a colliding drag and restores the last valid geometry', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.beginMove(3);
    controller.moveBy(const Offset(-300, 0));
    expect(controller.invalidPlacement, isTrue);
    controller.endMove();

    expect(controller.tables.firstWhere((table) => table.id == 3).x, 500);
    expect(controller.invalidPlacement, isFalse);
  });

  test('snaps close edges without introducing an overlap', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.beginMove(3);
    controller.moveBy(const Offset(-188, 0));

    expect(controller.interaction, LayoutInteraction.magnetPreview);
    expect(controller.invalidPlacement, isFalse);
    expect(controller.tables.firstWhere((table) => table.id == 3).x, 300);
    controller.endMove();
  });

  test('snaps rotated table bounds without reporting a false collision', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.rotateTable(3, math.pi / 4);
    controller.beginMove(3);
    controller.moveBy(const Offset(-188, 0));

    final moved = controller.tables.firstWhere((table) => table.id == 3);
    expect(controller.interaction, LayoutInteraction.magnetPreview);
    expect(controller.invalidPlacement, isFalse);
    expect(moved.x, closeTo(310.104, .01));
    controller.endMove();
  });

  test('resize respects the minimum size', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.beginResize(3);
    controller.resizeBy(const Offset(-1000, -1000));
    controller.endResize();

    final resized = controller.tables.firstWhere((table) => table.id == 3);
    expect(resized.width, RoomLayoutController.minimumTableSize);
    expect(resized.height, RoomLayoutController.minimumTableSize);
  });

  test('rotated table resize keeps its opposite visual corner anchored', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.setTableRotation(3, math.pi / 2);
    controller.beginResize(3);
    controller.resizeBy(const Offset(40, 20));
    controller.endResize();

    final resized = controller.tables.firstWhere((table) => table.id == 3);
    expect(resized.width, 140);
    expect(resized.height, 90);
    expect(resized.x, closeTo(470, .000001));
    expect(resized.y, closeTo(110, .000001));
  });

  test('cancel restores the last persisted layout', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.createTable('Terraza 2');
    expect(controller.tables, hasLength(4));
    expect(controller.dirty, isTrue);
    controller.cancel();
    expect(controller.tables, hasLength(3));
    expect(controller.dirty, isFalse);
  });

  test('walls can be selected, resized, rotated and explicitly deleted', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.addWall();
    final wallId = controller.selectedWallId;
    expect(wallId, isNotNull);
    controller.beginWallResize(wallId!);
    controller.resizeWallBy(const Offset(40, 6));
    controller.endWallResize();
    controller.rotateWall(wallId, math.pi / 2 - .04);
    expect(controller.walls.single.width, 250);
    expect(controller.walls.single.height, 16);
    expect(controller.walls.single.rotation, closeTo(math.pi / 2, .001));
    controller.beginWallMove(wallId);
    controller.moveWall(wallId, const Offset(30, 12));
    controller.finishWallEditing();
    expect(controller.walls.single.x, 390);
    expect(controller.walls.single.y, 352);

    controller.deleteSelected();
    expect(controller.walls, isEmpty);
  });

  test('tables persist rotation and background selection can be cleared', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.toggleSelection(3);
    controller.rotateTable(3, .4);
    expect(controller.snapshot().tables.last.rotation, closeTo(.4, .001));
    controller.clearSelection();
    expect(controller.selectedTableIds, isEmpty);
    expect(controller.selectedWallId, isNull);
  });

  test('table rotation snaps to every 90 degree axis', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.rotateTable(3, math.pi / 2 - .04);
    expect(controller.tables.last.rotation, closeTo(math.pi / 2, .000001));

    controller.setTableRotation(3, math.pi - .03);
    expect(controller.tables.last.rotation, closeTo(math.pi, .000001));
  });

  test('wall resize is cumulative from its interaction origin like tables', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.addWall();
    final wallId = controller.selectedWallId!;

    controller.beginWallResize(wallId);
    controller.resizeWallBy(const Offset(40, 6));
    controller.resizeWallBy(const Offset(60, 8));
    controller.endWallResize();

    expect(controller.walls.single.width, 270);
    expect(controller.walls.single.height, 18);
    expect(controller.interaction, LayoutInteraction.normal);
  });

  test('rotated wall resize keeps its opposite visual corner anchored', () {
    final controller = RoomLayoutController()..load(_layout());
    controller.addWall();
    final wallId = controller.selectedWallId!;
    controller.setWallRotation(wallId, math.pi / 2);

    controller.beginWallResize(wallId);
    controller.resizeWallBy(const Offset(40, 6));
    controller.endWallResize();

    final resized = controller.walls.single;
    expect(resized.width, 250);
    expect(resized.height, 16);
    expect(resized.x, closeTo(337, .000001));
    expect(resized.y, closeTo(357, .000001));
  });

  test('rotation handle points from the object center toward the cursor', () {
    final controller = RoomLayoutController()..load(_layout());
    const tableCenter = Offset(550, 135);

    controller.rotateTableToward(3, tableCenter + const Offset(0, -100));
    expect(controller.tables.last.rotation, closeTo(0, .000001));

    controller.rotateTableToward(3, tableCenter + const Offset(100, 0));
    expect(controller.tables.last.rotation, closeTo(math.pi / 2, .000001));

    controller.rotateTableToward(3, tableCenter + const Offset(100, 100));
    expect(controller.tables.last.rotation, closeTo(3 * math.pi / 4, .000001));

    controller.addWall();
    const wallCenter = Offset(465, 345);
    controller.rotateWallToward(
      controller.selectedWallId!,
      wallCenter + const Offset(-100, 0),
    );
    expect(controller.walls.single.rotation, closeTo(-math.pi / 2, .000001));
  });

  test(
    'new objects start around the requested viewport center without stacking',
    () {
      final controller = RoomLayoutController()
        ..load(
          const RoomLayoutModel(
            roomId: 1,
            roomName: 'Empty',
            tables: [],
            walls: [],
            groups: [],
          ),
        );
      const center = Offset(500, 400);
      controller.createTable('1', center: center);
      controller.createTable('2', center: center);
      controller.addWall(center: center);
      controller.addWall(center: center);

      expect(controller.tables.first.x, 435);
      expect(controller.tables.first.y, 359);
      expect(controller.tables.last.x, isNot(controller.tables.first.x));
      expect(controller.walls.last.x, isNot(controller.walls.first.x));
    },
  );

  test(
    'pasted table receives a unique identifier near the requested cursor',
    () {
      final controller = RoomLayoutController()..load(_layout());
      final source = controller.tables.first;
      controller.pasteTable(source, const Offset(700, 500));

      final pasted = controller.tables.last;
      expect(pasted.identifier, '1 copy');
      expect(pasted.x, 650);
      expect(pasted.y, 465);
      expect(controller.selectedTableIds, {pasted.id});
    },
  );

  test('moving a wall snaps its visual edge to another wall', () {
    final controller = RoomLayoutController()
      ..load(
        const RoomLayoutModel(
          roomId: 1,
          roomName: 'Walls',
          tables: [],
          walls: [],
          groups: [],
        ),
      );
    controller.addWall(center: const Offset(105, 5));
    controller.addWall(center: const Offset(435, 5));
    final movingId = controller.walls.last.id;

    controller.beginWallMove(movingId);
    controller.moveWall(movingId, const Offset(-112, 0));

    expect(controller.interaction, LayoutInteraction.magnetPreview);
    expect(controller.walls.last.x, closeTo(210, .000001));
    controller.finishWallEditing();
  });
}
