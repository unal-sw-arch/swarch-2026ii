import { useState } from "react";
import { Modal, StarRating, Icon } from '../../../../shared';

/**
 * Shared confirm-and-review modal used both by clients (confirm service +
 * review offerer) and offerers (mark done + review client).
 */
export function ReviewModal({ open, onClose, title, sub, ratingLabel, reviewLabel, confirmLabel, confirmClass = 'btn-primary', onConfirm }) {
    const [rating, setRating] = useState(0);
    const [comment, setComment] = useState('');

    const handleConfirm = () => {
        onConfirm?.({ rating, comment });
        setRating(0);
        setComment('');
    };

    return (
        <Modal open={open} onClose={onClose}>
            <div className="modal-title">{title}</div>
            <div className="modal-sub">{sub}</div>
            <div className="input-group">
                <label className="label">{ratingLabel}</label>
                <StarRating value={rating} onChange={setRating} />
            </div>
            <div className="input-group">
                <label className="label">{reviewLabel}</label>
                <textarea className="input" rows="3" placeholder="¿Cómo fue la experiencia?" value={comment} onChange={(e) => setComment(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
                <button className="btn btn-ghost btn-full" onClick={onClose}>Cancelar</button>
                <button className={`btn ${confirmClass} btn-full`} onClick={handleConfirm}><Icon name="check" size={15} />{confirmLabel}</button>
            </div>
        </Modal>
    );
}
