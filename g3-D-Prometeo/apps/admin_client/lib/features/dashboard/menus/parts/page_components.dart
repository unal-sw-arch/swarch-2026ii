// ignore_for_file: invalid_use_of_protected_member

part of '../menus_page.dart';

extension _MenusPageComponents on _MenusPageState {
  Widget _pageHeader({
    VoidCallback? back,
    required String title,
    required String subtitle,
    required List<Widget> actions,
  }) => Wrap(
    alignment: WrapAlignment.spaceBetween,
    crossAxisAlignment: WrapCrossAlignment.center,
    spacing: 16,
    runSpacing: 12,
    children: [
      SizedBox(
        width: 500,
        child: Row(
          children: [
            if (back != null) ...[
              IconButton(onPressed: back, icon: const Icon(Icons.arrow_back)),
              const SizedBox(width: 5),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: const TextStyle(color: Color(0xFF72767B)),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      Wrap(spacing: 10, runSpacing: 8, children: actions),
    ],
  );

  Widget _empty(String text) => Container(
    padding: const EdgeInsets.all(38),
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Text(text, style: const TextStyle(color: Color(0xFF72767B))),
  );

  String _assignmentText(RestaurantMenu menu) {
    String names(List<int> ids) => _rooms
        .where((room) => ids.contains(room.id))
        .map((room) => room.name)
        .join(', ');
    final primary = names(menu.primaryHallIds);
    final secondary = names(menu.secondaryHallIds);
    return '${_es ? 'Principal' : 'Primary'}: ${primary.isEmpty ? '—' : primary} · '
        '${_es ? 'Secundario' : 'Secondary'}: ${secondary.isEmpty ? '—' : secondary}';
  }
}
