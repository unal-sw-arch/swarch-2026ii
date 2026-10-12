part of '../order_editor_dialog.dart';

class _CategoryButton extends StatelessWidget {
  const _CategoryButton({
    super.key,
    required this.category,
    required this.onTap,
    this.square = false,
  });
  final ClientMenuCategory category;
  final VoidCallback onTap;
  final bool square;
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(16),
      side: const BorderSide(color: Color(0xFFE1E5E9)),
    ),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      splashColor: const Color(0x2271859B),
      hoverColor: const Color(0xFFF2F5F8),
      child: Padding(
        padding: square
            ? const EdgeInsets.all(14)
            : const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        child: square
            ? Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: const Color(0xFFEAF0F5),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(
                      Icons.restaurant_menu_rounded,
                      color: Color(0xFF647C94),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Flexible(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          category.name,
                          textAlign: TextAlign.center,
                          softWrap: true,
                          style: const TextStyle(
                            color: Color(0xFF292D32),
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            height: 1.15,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              )
            : Row(
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: const Color(0xFFEAF0F5),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.restaurant_menu_rounded,
                      color: Color(0xFF647C94),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          category.name,
                          style: const TextStyle(
                            color: Color(0xFF292D32),
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const Icon(
                    Icons.arrow_forward_ios_rounded,
                    size: 14,
                    color: Color(0xFF98A2AD),
                  ),
                ],
              ),
      ),
    ),
  );
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Icon(icon, size: 19, color: const Color(0xFF71859B)),
      const SizedBox(width: 8),
      Expanded(
        child: Text(
          label,
          softWrap: true,
          style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
        ),
      ),
    ],
  );
}

class _SpecialCategoryButton extends StatelessWidget {
  const _SpecialCategoryButton({
    super.key,
    required this.category,
    required this.selected,
    required this.quantity,
    required this.onTap,
  });

  final ClientMenuCategory category;
  final bool selected;
  final int quantity;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => AnimatedContainer(
    duration: const Duration(milliseconds: 180),
    decoration: BoxDecoration(
      color: selected ? const Color(0xFF71859B) : const Color(0xFFF2F5F8),
      borderRadius: BorderRadius.circular(12),
      border: Border.all(
        color: selected ? const Color(0xFF71859B) : const Color(0xFFDCE2E7),
      ),
    ),
    child: Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.add_circle_outline_rounded,
                size: 18,
                color: selected ? Colors.white : const Color(0xFF71859B),
              ),
              const SizedBox(width: 7),
              Text(
                category.name,
                style: TextStyle(
                  color: selected ? Colors.white : const Color(0xFF3D4751),
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (quantity > 0) ...[
                const SizedBox(width: 8),
                Container(
                  constraints: const BoxConstraints(minWidth: 22),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: selected ? Colors.white : const Color(0xFF71859B),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    '$quantity',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: selected ? const Color(0xFF71859B) : Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
              const SizedBox(width: 5),
              AnimatedRotation(
                turns: selected ? .5 : 0,
                duration: const Duration(milliseconds: 180),
                child: Icon(
                  Icons.keyboard_arrow_down_rounded,
                  size: 19,
                  color: selected ? Colors.white : const Color(0xFF71859B),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
