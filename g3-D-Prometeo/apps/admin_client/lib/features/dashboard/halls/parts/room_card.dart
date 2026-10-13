part of '../halls_page.dart';

class _RoomCard extends StatelessWidget {
  const _RoomCard({
    required this.room,
    required this.spanish,
    required this.onTap,
    required this.onManageMenus,
  });
  final RoomSummary room;
  final bool spanish;
  final VoidCallback onTap;
  final VoidCallback onManageMenus;

  String _money(double value) => formatPesos(value);

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(16),
    elevation: 1,
    shadowColor: const Color(0x18000000),
    child: InkWell(
      key: ValueKey('room-card-${room.id}'),
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const CircleAvatar(
                  backgroundColor: Color(0xFFE9EEF3),
                  child: Icon(
                    Icons.meeting_room_outlined,
                    color: Color(0xFF71859B),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    room.name,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                IconButton(
                  key: ValueKey('room-menus-${room.id}'),
                  tooltip: spanish ? 'Asignar menús' : 'Assign menus',
                  onPressed: onManageMenus,
                  icon: const Icon(
                    Icons.restaurant_menu_rounded,
                    color: Color(0xFF71859B),
                  ),
                ),
                const Icon(
                  Icons.arrow_forward_ios_rounded,
                  size: 14,
                  color: Color(0xFF9DA2A7),
                ),
              ],
            ),
            const Spacer(),
            Row(
              children: [
                Expanded(
                  child: _Statistic(
                    label: spanish ? 'Mesas' : 'Tables',
                    value: '${room.tableCount}',
                  ),
                ),
                Expanded(
                  child: _Statistic(
                    label: spanish ? 'Pedidos' : 'Orders',
                    value: '${room.orderCount}',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 17),
            Row(
              children: [
                Expanded(
                  child: _Statistic(
                    label: spanish ? 'Ventas' : 'Sales',
                    value: _money(room.totalSales),
                  ),
                ),
                Expanded(
                  child: _Statistic(
                    label: spanish ? 'Venta promedio' : 'Average sale',
                    value: _money(room.averageSale),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}
