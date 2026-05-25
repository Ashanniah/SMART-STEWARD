import { useEffect, useId } from 'react';
import { createPortal } from 'react-dom';
import { ExclamationTriangleIcon } from '@heroicons/react/24/solid';

/**
 * Confirmation dialog with optional themed icon.
 *
 * - `icon`: any React component (Heroicons or custom). Defaults to a warning triangle.
 * - `variant`: 'warning' | 'danger' | 'primary' | 'info' — controls icon & confirm-button color.
 * - `hideIcon`: pass `true` to render without an icon for backward compatibility.
 */
export default function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onCancel,
  onConfirm,
  icon: IconComponent,
  variant = 'warning',
  hideIcon = false,
}) {
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const onKey = (e) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (!open) return null;

  const Icon = IconComponent ?? ExclamationTriangleIcon;
  const showIcon = !hideIcon;

  return createPortal(
    <div
      className="confirm-modal-backdrop"
      role="presentation"
      onClick={onCancel}
    >
      <div
        className={`confirm-modal confirm-modal--with-icon confirm-modal--${variant}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        {showIcon ? (
          <div className={`confirm-modal__icon-wrap confirm-modal__icon-wrap--${variant}`} aria-hidden>
            <Icon className="confirm-modal__icon" />
          </div>
        ) : null}
        <h2
          id={titleId}
          className={`confirm-modal__title ${showIcon ? 'confirm-modal__title--with-icon' : ''}`}
        >
          {title}
        </h2>
        {message ? (
          <p className={`confirm-modal__message ${showIcon ? 'confirm-modal__message--centered' : ''}`}>
            {message}
          </p>
        ) : null}
        <div className={`confirm-modal__actions ${showIcon ? 'confirm-modal__actions--center' : ''}`}>
          <button type="button" className="confirm-modal__btn confirm-modal__btn--secondary" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`confirm-modal__btn confirm-modal__btn--primary confirm-modal__btn--${variant}`}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
