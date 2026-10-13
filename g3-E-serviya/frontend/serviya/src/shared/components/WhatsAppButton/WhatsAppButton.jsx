import { WhatsAppIcon } from '../Icon/Icon';

import './WhatsAppButton.css';

/**
 * Branded WhatsApp contact button. `block` makes it full width.
 */
export function WhatsAppButton({ label = 'WhatsApp', onClick, block = false, iconSize = 15 }) {
    return (
        <button
            type="button"
            className={`wa-btn ${block ? 'wa-btn-block' : ''}`}
            onClick={onClick}
        >
            <WhatsAppIcon size={iconSize} />
            {label}
        </button>
    );
}
