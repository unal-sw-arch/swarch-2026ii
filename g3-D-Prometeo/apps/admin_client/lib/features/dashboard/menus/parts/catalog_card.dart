part of '../menus_page.dart';

class _CatalogCard extends StatelessWidget {
  const _CatalogCard({
    super.key,
    required this.icon,
    required this.title,
    required this.lines,
    required this.onTap,
    this.badge,
    this.trailing,
  });
  final IconData icon;
  final String title;
  final List<String> lines;
  final VoidCallback onTap;
  final String? badge;
  final Widget? trailing;
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(16),
    elevation: 1,
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: const Color(0xFFE9EEF3),
              child: Icon(icon, color: const Color(0xFF71859B)),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          title,
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      if (badge != null)
                        Chip(
                          label: Text(badge!),
                          visualDensity: VisualDensity.compact,
                        ),
                    ],
                  ),
                  for (final line in lines)
                    Text(
                      line,
                      style: const TextStyle(
                        color: Color(0xFF72767B),
                        fontSize: 12,
                      ),
                    ),
                ],
              ),
            ),
            trailing ?? const Icon(Icons.chevron_right),
          ],
        ),
      ),
    ),
  );
}
