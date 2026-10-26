// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member

part of '../room_layout_controller.dart';

extension RoomLayoutGeometry on RoomLayoutController {
  void _replaceMoving(List<RoomTableModel> replacements) {
    final byId = {for (final table in replacements) table.id: table};
    tables = [for (final table in tables) byId[table.id] ?? table];
  }

  bool _hasCollision(List<RoomTableModel> moving, Set<int> movingIds) {
    final stationary = tables.where((table) => !movingIds.contains(table.id));
    for (final first in moving) {
      for (final second in stationary) {
        if (_intersects(first, second)) return true;
      }
    }
    return false;
  }

  Offset _snapCorrection(List<RoomTableModel> moving, Set<int> movingIds) {
    var bestX = RoomLayoutController.snapDistance + 1;
    var bestY = RoomLayoutController.snapDistance + 1;
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
            if (difference.abs() <= RoomLayoutController.snapDistance &&
                difference.abs() < bestX) {
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
            if (difference.abs() <= RoomLayoutController.snapDistance &&
                difference.abs() < bestY) {
              bestY = difference.abs();
              correctionY = difference;
            }
          }
        }
      }
    }
    final candidates = <Offset>[
      Offset(correctionX, correctionY),
      Offset(correctionX, 0),
      Offset(0, correctionY),
    ];
    for (final correction in candidates) {
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

  Offset _wallSnapCorrection(RoomWallModel moving, int movingId) {
    final movingBounds = _wallBounds(moving);
    var bestX = RoomLayoutController.snapDistance + 1;
    var bestY = RoomLayoutController.snapDistance + 1;
    var correctionX = 0.0;
    var correctionY = 0.0;
    for (final other in walls.where((wall) => wall.id != movingId)) {
      final otherBounds = _wallBounds(other);
      final verticallyNear =
          movingBounds.top <=
              otherBounds.bottom + RoomLayoutController.snapDistance &&
          movingBounds.bottom >=
              otherBounds.top - RoomLayoutController.snapDistance;
      final horizontallyNear =
          movingBounds.left <=
              otherBounds.right + RoomLayoutController.snapDistance &&
          movingBounds.right >=
              otherBounds.left - RoomLayoutController.snapDistance;
      if (verticallyNear) {
        for (final difference in [
          otherBounds.left - movingBounds.left,
          otherBounds.left - movingBounds.right,
          otherBounds.right - movingBounds.left,
          otherBounds.right - movingBounds.right,
        ]) {
          if (difference.abs() <= RoomLayoutController.snapDistance &&
              difference.abs() < bestX) {
            bestX = difference.abs();
            correctionX = difference;
          }
        }
      }
      if (horizontallyNear) {
        for (final difference in [
          otherBounds.top - movingBounds.top,
          otherBounds.top - movingBounds.bottom,
          otherBounds.bottom - movingBounds.top,
          otherBounds.bottom - movingBounds.bottom,
        ]) {
          if (difference.abs() <= RoomLayoutController.snapDistance &&
              difference.abs() < bestY) {
            bestY = difference.abs();
            correctionY = difference;
          }
        }
      }
    }
    return Offset(correctionX, correctionY);
  }

  Offset _freeTablePosition(
    Offset center, {
    required double width,
    required double height,
    double rotation = 0,
  }) {
    for (final offset in _placementOffsets()) {
      final candidateCenter = center + offset;
      final candidate = RoomTableModel(
        id: _nextTemporaryId,
        identifier: '',
        x: candidateCenter.dx - width / 2,
        y: candidateCenter.dy - height / 2,
        width: width,
        height: height,
        rotation: rotation,
      );
      if (tables.every((table) => !_intersects(candidate, table)) &&
          walls.every(
            (wall) => !_bounds(candidate).overlaps(_wallBounds(wall)),
          )) {
        return Offset(candidate.x, candidate.y);
      }
    }
    return Offset(center.dx - width / 2 + 480, center.dy - height / 2 + 480);
  }

  Offset _freeWallPosition(
    Offset center, {
    required double width,
    required double height,
  }) {
    for (final offset in _placementOffsets()) {
      final candidateCenter = center + offset;
      final candidate = RoomWallModel(
        id: _nextTemporaryId,
        x: candidateCenter.dx - width / 2,
        y: candidateCenter.dy - height / 2,
        width: width,
        height: height,
        rotation: 0,
      );
      if (walls.every(
            (wall) => !_wallBounds(candidate).overlaps(_wallBounds(wall)),
          ) &&
          tables.every(
            (table) => !_wallBounds(candidate).overlaps(_bounds(table)),
          )) {
        return Offset(candidate.x, candidate.y);
      }
    }
    return Offset(center.dx - width / 2 + 480, center.dy - height / 2 + 480);
  }

  Iterable<Offset> _placementOffsets() sync* {
    yield Offset.zero;
    const step = 36.0;
    for (var ring = 1; ring <= 14; ring++) {
      for (var x = -ring; x <= ring; x++) {
        yield Offset(x * step, -ring * step);
        yield Offset(x * step, ring * step);
      }
      for (var y = -ring + 1; y < ring; y++) {
        yield Offset(-ring * step, y * step);
        yield Offset(ring * step, y * step);
      }
    }
  }

  String _copiedIdentifier(String original) {
    final used = tables.map((table) => table.identifier).toSet();
    var candidate = '$original copy';
    var suffix = 2;
    while (used.contains(candidate)) {
      candidate = '$original copy ${suffix++}';
    }
    return candidate;
  }

  bool _intersects(RoomTableModel a, RoomTableModel b) =>
      _bounds(a).left <
          _bounds(b).right - RoomLayoutController.collisionTolerance &&
      _bounds(a).right >
          _bounds(b).left + RoomLayoutController.collisionTolerance &&
      _bounds(a).top <
          _bounds(b).bottom - RoomLayoutController.collisionTolerance &&
      _bounds(a).bottom >
          _bounds(b).top + RoomLayoutController.collisionTolerance;

  double _snapRotation(double angle) {
    const fullTurn = math.pi * 2;
    var normalized = angle % fullTurn;
    if (normalized > math.pi) normalized -= fullTurn;
    if (normalized <= -math.pi) normalized += fullTurn;
    final quarterTurn = math.pi / 2;
    final nearestAxis = (normalized / quarterTurn).round() * quarterTurn;
    return (normalized - nearestAxis).abs() <=
            RoomLayoutController.rotationSnapThreshold
        ? nearestAxis
        : normalized;
  }

  double? _rotationTowardPointer(Offset center, Offset pointer) {
    final direction = pointer - center;
    if (direction.distanceSquared < 1) return null;
    // El handle nace sobre el centro de la cara superior. Por eso el ángulo
    // visual del cursor (atan2) necesita un cuarto de vuelta para convertirse
    // en la rotación del rectángulo: la cara queda perpendicular al cursor y
    // el handle apunta exactamente hacia él.
    return math.atan2(direction.dy, direction.dx) + math.pi / 2;
  }

  Offset _resizedPosition({
    required double x,
    required double y,
    required double oldWidth,
    required double oldHeight,
    required double newWidth,
    required double newHeight,
    required double rotation,
  }) {
    final halfWidthDelta = (newWidth - oldWidth) / 2;
    final halfHeightDelta = (newHeight - oldHeight) / 2;
    final cosine = math.cos(rotation);
    final sine = math.sin(rotation);

    // Transform.rotate gira alrededor del centro. Al redimensionar desde la
    // esquina inferior derecha, el centro debe desplazarse en los ejes
    // rotados para conservar inmóvil la esquina superior izquierda visual.
    final rotatedCenterDelta = Offset(
      halfWidthDelta * cosine - halfHeightDelta * sine,
      halfWidthDelta * sine + halfHeightDelta * cosine,
    );
    return Offset(
      x + rotatedCenterDelta.dx - halfWidthDelta,
      y + rotatedCenterDelta.dy - halfHeightDelta,
    );
  }

  Rect _bounds(RoomTableModel table) {
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

  Rect _wallBounds(RoomWallModel wall) {
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
