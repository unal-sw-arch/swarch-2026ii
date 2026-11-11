"""Genera el comprobante de pago en PDF (reportlab) a partir del evento `order.paid`."""
import io
from decimal import Decimal

from reportlab.lib import colors
from reportlab.lib.pagesizes import A6
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


def _money(value) -> str:
    return f"{Decimal(str(value)):,.2f}"


def build_receipt_pdf(event: dict) -> bytes:
    styles = getSampleStyleSheet()
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer, pagesize=A6, leftMargin=8 * mm, rightMargin=8 * mm, topMargin=8 * mm, bottomMargin=8 * mm,
        title=f"Comprobante {event['order_id']}",
    )
    where = (
        f"Mesa {event['table_number']}" if event.get("order_type") == "en_mesa" else "Para llevar"
    )
    story = [
        Paragraph("<b>Panadería Express</b>", styles["Title"]),
        Paragraph("Comprobante de pago (simulado)", styles["Normal"]),
        Spacer(1, 4 * mm),
        Paragraph(f"Pedido: {event['order_id']}", styles["Normal"]),
        Paragraph(f"Pagado: {event['paid_at']}", styles["Normal"]),
        Paragraph(f"Servicio: {where}", styles["Normal"]),
        Spacer(1, 4 * mm),
    ]
    rows = [["Producto", "Cant.", "Precio", "Subtotal"]]
    for item in event.get("items", []):
        unit = Decimal(str(item.get("unit_price", "0")))
        rows.append([item["name"], str(item["quantity"]), _money(unit), _money(unit * item["quantity"])])
    rows.append(["", "", "TOTAL", _money(event["total"])])
    table = Table(rows, colWidths=[40 * mm, 12 * mm, 18 * mm, 20 * mm], repeatRows=1)
    table.setStyle(TableStyle([
        ("FONTSIZE", (0, 0), (-1, -1), 7),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, -1), (-1, -1), "Helvetica-Bold"),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.black),
        ("LINEABOVE", (0, -1), (-1, -1), 0.5, colors.black),
        ("ALIGN", (1, 0), (-1, -1), "RIGHT"),
    ]))
    story.append(table)
    doc.build(story)
    return buffer.getvalue()
