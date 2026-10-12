part of '../admin_dashboard.dart';

class _NavigationPanel extends StatelessWidget {
  const _NavigationPanel({
    required this.adminName,
    required this.spanish,
    required this.onLogout,
    this.hideIdentity = false,
    this.closeOnSelect = false,
    required this.selected,
    required this.onSelected,
  });

  final String adminName;
  final bool spanish;
  final VoidCallback onLogout;
  final bool hideIdentity;
  final bool closeOnSelect;
  final _AdminSection selected;
  final ValueChanged<_AdminSection> onSelected;

  void _select(BuildContext context, _AdminSection section) {
    onSelected(section);
    if (closeOnSelect) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: _surface,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(22, 24, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (!hideIdentity) ...[
              _AdminIdentity(name: adminName),
              const SizedBox(height: 40),
            ],
            _NavItem(
              icon: Icons.dashboard_outlined,
              label: spanish ? 'Resumen' : 'Overview',
              selected: selected == _AdminSection.overview,
              onTap: () => _select(context, _AdminSection.overview),
            ),
            _NavItem(
              icon: Icons.menu_book_outlined,
              label: spanish ? 'Administrar menús' : 'Manage menus',
              selected: selected == _AdminSection.menus,
              onTap: () => _select(context, _AdminSection.menus),
            ),
            _NavItem(
              icon: Icons.grass_outlined,
              label: spanish ? 'Ingredientes' : 'Ingredients',
              selected: selected == _AdminSection.ingredients,
              onTap: () => _select(context, _AdminSection.ingredients),
            ),
            _NavItem(
              icon: Icons.meeting_room_outlined,
              label: spanish ? 'Salones y mesas' : 'Halls and tables',
              selected: selected == _AdminSection.halls,
              onTap: () => _select(context, _AdminSection.halls),
            ),
            _NavItem(
              icon: Icons.groups_outlined,
              label: spanish ? 'Empleados' : 'Employees',
              selected: selected == _AdminSection.employees,
              onTap: () => _select(context, _AdminSection.employees),
            ),
            _NavItem(
              icon: Icons.delete_sweep_outlined,
              label: spanish ? 'Eliminar historial' : 'Delete history',
              selected: selected == _AdminSection.history,
              onTap: () => _select(context, _AdminSection.history),
            ),
            const Spacer(),
            _NavItem(
              key: const ValueKey('dashboard-logout'),
              icon: Icons.logout,
              label: spanish ? 'Cerrar sesión' : 'Logout',
              onTap: onLogout,
            ),
          ],
        ),
      ),
    );
  }
}

class _AdminIdentity extends StatelessWidget {
  const _AdminIdentity({required this.name, this.compact = false});

  final String name;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        CircleAvatar(
          radius: compact ? 16 : 19,
          backgroundColor: const Color(0xFFE8EDF2),
          child: const Icon(Icons.person_outline, color: _accent, size: 21),
        ),
        const SizedBox(width: 10),
        Flexible(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                name,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: _ink,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (!compact)
                const Text(
                  'Administrator',
                  style: TextStyle(color: _muted, fontSize: 11),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    super.key,
    required this.icon,
    required this.label,
    this.selected = false,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Material(
        color: selected ? const Color(0xFFF0F3F6) : Colors.transparent,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          onTap: onTap ?? () {},
          borderRadius: BorderRadius.circular(10),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
            child: Row(
              children: [
                Icon(icon, size: 21, color: selected ? _accent : _ink),
                const SizedBox(width: 13),
                Expanded(
                  child: Text(
                    label,
                    style: TextStyle(
                      color: _ink,
                      fontSize: 13,
                      fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
