import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/foundation.dart';

import 'room_layout_models.dart';

part 'room_layout/table_interactions.dart';
part 'room_layout/table_management.dart';
part 'room_layout/wall_editing.dart';
part 'room_layout/clipboard_and_queries.dart';
part 'room_layout/geometry.dart';

enum LayoutInteraction {
  normal,
  navigating,
  tablePressed,
  draggingTable,
  magnetPreview,
  resizingTable,
  resizingWall,
  rotatingObject,
  editingWalls,
  creatingTable,
}

class RoomLayoutController extends ChangeNotifier {
  static const minimumTableSize = 44.0;
  static const snapDistance = 12.0;
  static const collisionTolerance = .05;
  static const rotationSnapThreshold = math.pi / 36;

  RoomLayoutModel? _persisted;
  List<RoomTableModel> tables = [];
  List<RoomWallModel> walls = [];
  List<RoomTableGroupModel> groups = [];
  Set<int> selectedTableIds = {};
  int? selectedWallId;
  LayoutInteraction interaction = LayoutInteraction.normal;
  bool invalidPlacement = false;
  bool dirty = false;
  int _nextTemporaryId = -1;
  Map<int, RoomTableModel> _interactionOrigin = {};
  RoomWallModel? _wallMoveOrigin;
  RoomWallModel? _wallResizeOrigin;

  void load(RoomLayoutModel layout) {
    _persisted = layout;
    _replaceWith(layout);
  }

  void _replaceWith(RoomLayoutModel layout) {
    tables = [...layout.tables];
    walls = [...layout.walls];
    groups = layout.groups
        .map(
          (group) => RoomTableGroupModel(
            id: group.id,
            identifier: group.identifier,
            tableIds: [...group.tableIds],
          ),
        )
        .toList();
    selectedTableIds = {};
    selectedWallId = null;
    interaction = LayoutInteraction.normal;
    invalidPlacement = false;
    dirty = false;
    _nextTemporaryId = -1;
    _wallMoveOrigin = null;
    _wallResizeOrigin = null;
    notifyListeners();
  }

  RoomLayoutModel snapshot() => RoomLayoutModel(
    roomId: _persisted?.roomId ?? 1,
    roomName: _persisted?.roomName ?? 'Main Hall',
    tables: [...tables],
    walls: [...walls],
    groups: groups
        .map(
          (group) => RoomTableGroupModel(
            id: group.id,
            identifier: group.identifier,
            tableIds: [...group.tableIds],
          ),
        )
        .toList(),
  );

  void acceptSaved(RoomLayoutModel layout) => load(layout);
  void cancel() {
    if (_persisted != null) _replaceWith(_persisted!);
  }
}
