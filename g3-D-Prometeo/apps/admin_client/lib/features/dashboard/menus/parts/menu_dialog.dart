part of '../menus_page.dart';

enum _AssignmentMode { none, primary, secondary }

class _MenuDraft {
  const _MenuDraft(this.name, this.assignments);
  final String name;
  final List<MenuHallAssignment> assignments;
}

class _MenuDialog extends StatefulWidget {
  const _MenuDialog({required this.spanish, required this.rooms});
  final bool spanish;
  final List<RoomSummary> rooms;
  @override
  State<_MenuDialog> createState() => _MenuDialogState();
}

class _MenuDialogState extends State<_MenuDialog> {
  final _name = TextEditingController();
  final Map<int, _AssignmentMode> _modes = {};
  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.spanish ? 'Nuevo menú' : 'New menu'),
    content: SizedBox(
      width: 480,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              key: const ValueKey('new-menu-name'),
              controller: _name,
              autofocus: true,
              decoration: InputDecoration(
                labelText: widget.spanish ? 'Nombre' : 'Name',
              ),
            ),
            const SizedBox(height: 16),
            for (final room in widget.rooms)
              DropdownButtonFormField<_AssignmentMode>(
                initialValue: _modes[room.id] ?? _AssignmentMode.none,
                decoration: InputDecoration(labelText: room.name),
                items: [
                  DropdownMenuItem(
                    value: _AssignmentMode.none,
                    child: Text(
                      widget.spanish ? 'No asignado' : 'Not assigned',
                    ),
                  ),
                  DropdownMenuItem(
                    value: _AssignmentMode.primary,
                    child: Text(
                      widget.spanish ? 'Menú principal' : 'Primary menu',
                    ),
                  ),
                  DropdownMenuItem(
                    value: _AssignmentMode.secondary,
                    child: Text(
                      widget.spanish ? 'Menú secundario' : 'Secondary menu',
                    ),
                  ),
                ],
                onChanged: (value) => setState(() => _modes[room.id] = value!),
              ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(widget.spanish ? 'Cancelar' : 'Cancel'),
      ),
      FilledButton(
        key: const ValueKey('submit-menu'),
        onPressed: () {
          if (_name.text.trim().length < 2) return;
          Navigator.pop(
            context,
            _MenuDraft(_name.text.trim(), [
              for (final room in widget.rooms)
                if ((_modes[room.id] ?? _AssignmentMode.none) !=
                    _AssignmentMode.none)
                  MenuHallAssignment(
                    hallId: room.id,
                    isPrimary: _modes[room.id] == _AssignmentMode.primary,
                  ),
            ]),
          );
        },
        child: Text(widget.spanish ? 'Crear' : 'Create'),
      ),
    ],
  );
}
