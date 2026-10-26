import React from 'react';
import { InventoryAdminPanel } from '../equipment/components/InventoryAdminPanel';

export function SystemInventoryPage(): React.JSX.Element {
  return <InventoryAdminPanel isSystemAdmin={true} />;
}
