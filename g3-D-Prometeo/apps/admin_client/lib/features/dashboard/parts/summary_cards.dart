part of '../admin_dashboard.dart';

class _TopItemCard extends StatelessWidget {
  const _TopItemCard({required this.spanish, required this.metrics});
  final bool spanish;
  final Future<AdminOverviewMetrics> metrics;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<AdminOverviewMetrics>(
      future: metrics,
      builder: (context, snapshot) {
        final product = snapshot.data?.topProduct;
        return _Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                spanish ? 'MÁS VENDIDO' : 'TOP SELLING ITEM',
                style: const TextStyle(color: _muted, fontSize: 11),
              ),
              const SizedBox(height: 10),
              Container(
                height: 112,
                width: double.infinity,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(9),
                  gradient: const LinearGradient(
                    colors: [Color(0xFFD8C8B4), Color(0xFF899389)],
                  ),
                ),
                child: const Center(
                  child: Icon(
                    Icons.ramen_dining,
                    color: Colors.white,
                    size: 54,
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Text(
                product?.name ??
                    (spanish ? 'Sin ventas hoy' : 'No sales today'),
                style: const TextStyle(
                  color: _ink,
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                product == null
                    ? '—'
                    : '${formatPesos(product.value)} / ${spanish ? 'unidad' : 'unit'} · ${product.quantity} ${spanish ? 'unidades' : 'units'}',
                style: const TextStyle(color: _muted, fontSize: 12),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _CategoryCard extends StatelessWidget {
  const _CategoryCard({required this.spanish, required this.metrics});
  final bool spanish;
  final Future<AdminOverviewMetrics> metrics;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<AdminOverviewMetrics>(
      future: metrics,
      builder: (context, snapshot) {
        final categories = snapshot.data?.categories ?? const [];
        final total = categories.fold<double>(
          0,
          (sum, item) => sum + item.value,
        );
        const colors = [
          Color(0xFF52657A),
          Color(0xFF8798AC),
          Color(0xFFD9E8C8),
          Color(0xFFE2E3E1),
        ];
        final rows = [
          for (var index = 0; index < categories.length; index++)
            (
              categories[index].name,
              total == 0 ? 0 : (categories[index].value / total * 100).round(),
              colors[index % colors.length],
            ),
        ];
        return _Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                spanish ? 'VENTAS POR CATEGORÍA' : 'CATEGORY BREAKDOWN',
                style: const TextStyle(color: _muted, fontSize: 11),
              ),
              const SizedBox(height: 15),
              for (final row in rows)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                          color: row.$3,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          row.$1,
                          style: const TextStyle(color: _ink, fontSize: 12),
                        ),
                      ),
                      Text(
                        '${row.$2}%',
                        style: const TextStyle(color: _muted, fontSize: 10),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 3),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: Row(
                  children: rows
                      .map(
                        (row) => Expanded(
                          flex: row.$2 == 0 ? 1 : row.$2,
                          child: Container(height: 8, color: row.$3),
                        ),
                      )
                      .toList(),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
