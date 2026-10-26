export type SplitChargeLine = {
  unitValue: number;
  paidHalfUnits: number;
  halfUnits: number;
};

/** Charge the difference in cumulative whole pesos so split halves reconcile. */
export function splitChargeSubtotal(lines: SplitChargeLine[]): number {
  return lines.reduce((sum, line) => {
    const previous = Math.floor(line.unitValue * line.paidHalfUnits / 2);
    const next = Math.floor(
      line.unitValue * (line.paidHalfUnits + line.halfUnits) / 2,
    );
    return sum + next - previous;
  }, 0);
}
