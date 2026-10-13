import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/foundation.dart';

import '../../models/client_room.dart';

enum LiveLayoutInteraction {
  normal,
  tablePressed,
  draggingTable,
  magnetPreview,
}

class LiveRoomController extends ChangeNotifier {
  static const snapDistance = 12.0;
  static const collisionTolerance = .05;

  LiveRoomLayout? _persisted;
  List<LiveRoomTable> tables = [];
  List<LiveRoomWall> walls = [];
  List<LiveTableGroup> groups = [];
  Set<int> selectedTableIds = {};
  LiveLayoutInteraction interaction = LiveLayoutInteraction.normal;
  bool invalidPlacement = false;
  bool dirty = false;
  int _nextTemporaryId = -1;
  Map<int, LiveRoomTable> _moveOrigin = {};

  Rect get contentBounds {
    Rect? result;
    for (final wall in walls) {
      final bounds = _wallBounds(wall);
      result = result == null ? bounds : result.expandToInclude(bounds);
    }
    for (final table in tables) {
      final bounds = _bounds(table);
      result = result == null ? bounds : result.expandToInclude(bounds);
    }
    return result ?? const Rect.fromLTWH(-100, -100, 200, 200);
  }

  void load(LiveRoomLayout layout) {
    _persisted = layout;
    tables = [...layout.tables];
    walls = [...layout.walls];
    groups = [
      for (final group in layout.groups)
        LiveTableGroup(
          id: group.id,
          identifier: group.identifier,
          tableIds: [...group.tableIds],
        ),
    ];
    selectedTableIds = {};
    interaction = LiveLayoutInteraction.normal;
    invalidPlacement = false;
    dirty = false;
    _nextTemporaryId = -1;
    notifyListeners();
  }

  LiveRoomLayout snapshot() => LiveRoomLayout(
    roomId: _persisted!.roomId,
    roomName: _persisted!.roomName,
    tables: [...tables],
    walls: [...walls],
    groups: [
      for (final group in groups)
        LiveTableGroup(
          id: group.id,
          identifier: group.identifier,
          tableIds: [...group.tableIds],
        ),
    ],
  );

  void acceptSaved(LiveRoomLayout layout) => load(layout);

  void replaceFromRealtime(LiveRoomLayout layout) {
    if (interaction != LiveLayoutInteraction.normal || dirty) return;
    final selection = {...selectedTableIds};
    load(layout);
    selectedTableIds = selection
        .where((id) => tables.any((table) => table.id == id))
        .toSet();
    notifyListeners();
  }

  void applyOrderStatuses(Map<int, String> statuses) {
    tables = [
      for (final table in tables)
        table.copyWith(status: statuses[table.id] ?? 'available'),
    ];
    notifyListeners();
  }

  void toggleSelection(int id) {
    if (selectedTableIds.contains(id)) {
      selectedTableIds = {...selectedTableIds}..remove(id);
    } else {
      selectedTableIds = {...selectedTableIds, id};
    }
    interaction = LiveLayoutInteraction.normal;
    notifyListeners();
  }

  void selectTableOrGroup(int id) {
    final group = groupForTable(id);
    selectedTableIds = group == null ? {id} : group.tableIds.toSet();
    interaction = LiveLayoutInteraction.normal;
    notifyListeners();
  }

  void clearSelection() {
    selectedTableIds = {};
    interaction = LiveLayoutInteraction.normal;
    invalidPlacement = false;
    notifyListeners();
  }

  void pressTable() {
    interaction = LiveLayoutInteraction.tablePressed;
    notifyListeners();
  }

  void cancelTablePress() {
    if (interaction == LiveLayoutInteraction.tablePressed) {
      interaction = LiveLayoutInteraction.normal;
      notifyListeners();
    }
  }

  void beginMove(int id) {
    final group = groupForTable(id);
    selectedTableIds = group == null ? {id} : group.tableIds.toSet();
    _moveOrigin = {
      for (final table in tables)
        if (selectedTableIds.contains(table.id)) table.id: table,
    };
    interaction = LiveLayoutInteraction.tablePressed;
    invalidPlacement = false;
    notifyListeners();
  }

  void moveBy(Offset delta) {
    if (_moveOrigin.isEmpty) return;
    final movingIds = _moveOrigin.keys.toSet();
    final proposed = _moveOrigin.values
        .map(
          (table) =>
              table.copyWith(x: table.x + delta.dx, y: table.y + delta.dy),
        )
        .toList();
    final correction = _snapCorrection(proposed, movingIds);
    final moved = proposed
        .map(
          (table) => table.copyWith(
            x: table.x + correction.dx,
            y: table.y + correction.dy,
          ),
        )
        .toList();
    _replaceTables(moved);
    invalidPlacement = _hasCollision(moved, movingIds);
    interaction = correction == Offset.zero
        ? LiveLayoutInteraction.draggingTable
        : LiveLayoutInteraction.magnetPreview;
    notifyListeners();
  }

  bool endMove() {
    final accepted = !invalidPlacement;
    if (!accepted) {
      _replaceTables(_moveOrigin.values.toList());
    } else {
      dirty = true;
    }
    _moveOrigin = {};
    invalidPlacement = false;
    interaction = LiveLayoutInteraction.normal;
    notifyListeners();
    return accepted;
  }

  void cancelMove() {
    if (_moveOrigin.isNotEmpty) {
      _replaceTables(_moveOrigin.values.toList());
    }
    _moveOrigin = {};
    invalidPlacement = false;
    interaction = LiveLayoutInteraction.normal;
    notifyListeners();
  }

  LiveTableGroup? groupForTable(int tableId) {
    for (final group in groups) {
      if (group.tableIds.contains(tableId)) return group;
    }
    return null;
  }

  bool get canGroup => selectedTableIds.length >= 2;
  bool get canUngroup =>
      selectedTableIds.any((id) => groupForTable(id) != null);
  bool get canToggleGroup => canUngroup || selectedTableIds.length >= 2;

  void groupSelected({String? identifier}) {
    if (!canGroup) return;
    groups = groups
        .where(
          (group) =>
              group.tableIds.every((id) => !selectedTableIds.contains(id)),
        )
        .toList();
    groups = [
      ...groups,
      LiveTableGroup(
        id: _nextTemporaryId--,
        identifier: _groupIdentifier(identifier),
        tableIds: selectedTableIds.toList(),
      ),
    ];
    dirty = true;
    notifyListeners();
  }

  void ungroupSelected() {
    if (!canUngroup) return;
    groups = groups
        .where(
          (group) =>
              group.tableIds.every((id) => !selectedTableIds.contains(id)),
        )
        .toList();
    dirty = true;
    notifyListeners();
  }

  void toggleSelectedGroup({String? identifier}) {
    if (!canToggleGroup) return;
    if (canUngroup) {
      final selected = {...selectedTableIds};
      groups = groups
          .where((group) {
            return group.tableIds.every((id) => !selected.contains(id));
          })
          .where((group) => group.tableIds.length >= 2)
          .toList();
    } else {
      groups = [
        ...groups,
        LiveTableGroup(
          id: _nextTemporaryId--,
          identifier: _groupIdentifier(identifier),
          tableIds: selectedTableIds.toList(),
        ),
      ];
    }
    dirty = true;
    notifyListeners();
  }

  String _groupIdentifier(String? requested) {
    final custom = requested?.trim() ?? '';
    if (custom.isNotEmpty) return custom;
    return tables
        .where((table) => selectedTableIds.contains(table.id))
        .map((table) => table.identifier)
        .join(' + ');
  }

  void _replaceTables(List<LiveRoomTable> replacements) {
    final byId = {for (final table in replacements) table.id: table};
    tables = [for (final table in tables) byId[table.id] ?? table];
  }

  bool _hasCollision(List<LiveRoomTable> moving, Set<int> movingIds) {
    final stationary = tables.where((table) => !movingIds.contains(table.id));
    for (final first in moving) {
      for (final second in stationary) {
        if (_intersects(first, second)) return true;
      }
    }
    return false;
  }

  Offset _snapCorrection(List<LiveRoomTable> moving, Set<int> movingIds) {
    var bestX = snapDistance + 1;
    var bestY = snapDistance + 1;
    var correctionX = 0.0;
    var correctionY = 0.0;
    for (final movingTable in moving) {
      final movingBounds = _bounds(movingTable);
      for (final other in tables.where(
        (table) => !movingIds.contains(table.id),
      )) {
        final otherBounds = _bounds(other);
        final verticalOverlap =
            movingBounds.top < otherBounds.bottom &&
            movingBounds.bottom > otherBounds.top;
        final horizontalOverlap =
            movingBounds.left < otherBounds.right &&
            movingBounds.right > otherBounds.left;
        if (verticalOverlap) {
          for (final difference in [
            otherBounds.left - movingBounds.right,
            otherBounds.right - movingBounds.left,
          ]) {
            if (difference.abs() <= snapDistance && difference.abs() < bestX) {
              bestX = difference.abs();
              correctionX = difference;
            }
          }
        }
        if (horizontalOverlap) {
          for (final difference in [
            otherBounds.top - movingBounds.bottom,
            otherBounds.bottom - movingBounds.top,
          ]) {
            if (difference.abs() <= snapDistance && difference.abs() < bestY) {
              bestY = difference.abs();
              correctionY = difference;
            }
          }
        }
      }
    }
    for (final correction in [
      Offset(correctionX, correctionY),
      Offset(correctionX, 0),
      Offset(0, correctionY),
    ]) {
      if (correction == Offset.zero) continue;
      final corrected = moving
          .map(
            (table) => table.copyWith(
              x: table.x + correction.dx,
              y: table.y + correction.dy,
            ),
          )
          .toList();
      if (!_hasCollision(corrected, movingIds)) return correction;
    }
    return Offset.zero;
  }

  bool _intersects(LiveRoomTable first, LiveRoomTable second) {
    final a = _bounds(first);
    final b = _bounds(second);
    return a.left < b.right - collisionTolerance &&
        a.right > b.left + collisionTolerance &&
        a.top < b.bottom - collisionTolerance &&
        a.bottom > b.top + collisionTolerance;
  }

  Rect _bounds(LiveRoomTable table) {
    final cosine = math.cos(table.rotation).abs();
    final sine = math.sin(table.rotation).abs();
    final width = table.width * cosine + table.height * sine;
    final height = table.width * sine + table.height * cosine;
    return Rect.fromCenter(
      center: Offset(table.x + table.width / 2, table.y + table.height / 2),
      width: width,
      height: height,
    );
  }

  Rect _wallBounds(LiveRoomWall wall) {
    final cosine = math.cos(wall.rotation).abs();
    final sine = math.sin(wall.rotation).abs();
    final width = wall.width * cosine + wall.height * sine;
    final height = wall.width * sine + wall.height * cosine;
    return Rect.fromCenter(
      center: Offset(wall.x + wall.width / 2, wall.y + wall.height / 2),
      width: width,
      height: height,
    );
  }
}
