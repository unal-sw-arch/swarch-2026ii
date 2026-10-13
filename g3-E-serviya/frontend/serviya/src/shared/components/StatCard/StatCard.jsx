import { Icon } from '../Icon/Icon';

/**
 * Dashboard statistic tile: icon, big number, label and optional change note.
 * `variant` maps to the colored icon backgrounds (success / warn / danger).
 */
export function StatCard({ icon, value, label, variant = '', change, changeUp, fill = 'none' }) {
    return (
        <div className="stat-card">
            <div className={`stat-ico ${variant}`}><Icon name={icon} size={18} fill={fill} /></div>
            <div className="stat-n">{value}</div>
            <div className="stat-l">{label}</div>
            {change && <div className={`stat-ch ${changeUp ? 'stat-up' : 'stat-dn'}`}>{change}</div>}
        </div>
    );
}
