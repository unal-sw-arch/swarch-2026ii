// ignore_for_file: invalid_use_of_protected_member

part of '../hall_layout_page.dart';

extension _HallPageChrome on _HallLayoutPageState {
  ClientSession _liveSession() => ClientSession(
    token: widget.token,
    expiresAt: DateTime.now().add(const Duration(hours: 12)),
    user: const ClientUser(
      id: 0,
      fullName: 'Administrator',
      username: 'admin',
      role: 'admin',
    ),
  );

  Widget _header() => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 11),
    child: Wrap(
      spacing: 12,
      runSpacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (widget.onBack != null)
          IconButton(
            key: const ValueKey('back-to-rooms'),
            onPressed: widget.onBack,
            tooltip: _es ? 'Volver a salones' : 'Back to rooms',
            icon: const Icon(Icons.arrow_back),
          ),
        SizedBox(
          width: 220,
          child: Text(
            _layout.snapshot().roomName,
            style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
          ),
        ),
        SegmentedButton<HallViewMode>(
          key: const ValueKey('hall-view-mode'),
          segments: [
            ButtonSegment(
              value: HallViewMode.live,
              label: Text(_es ? 'Vista en vivo' : 'Live View'),
            ),
            if (widget.isAdmin)
              ButtonSegment(
                value: HallViewMode.edit,
                label: Text(_es ? 'Editar layout' : 'Edit Layout'),
              ),
          ],
          selected: {_mode},
          onSelectionChanged: (selection) {
            setState(() => _mode = selection.first);
            if (_mode == HallViewMode.live) _load();
          },
          showSelectedIcon: true,
        ),
        if (_editing) ...[
          TextButton(
            key: const ValueKey('cancel-layout'),
            onPressed: _layout.dirty ? _layout.cancel : null,
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton.icon(
            key: const ValueKey('save-layout'),
            onPressed: _layout.dirty && !_saving ? _save : null,
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF52677D),
            ),
            icon: _saving
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.save_outlined, size: 19),
            label: Text(_es ? 'Guardar cambios' : 'Save Changes'),
          ),
        ],
      ],
    ),
  );
}
