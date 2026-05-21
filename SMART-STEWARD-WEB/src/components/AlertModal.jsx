import { useEffect, useId } from 'react';
import { createPortal } from 'react-dom';
import { CheckCircleIcon } from '@heroicons/react/24/solid';

/** Single-action dialog for success or informational messages. */
export default function AlertModal({
  open,
  title,
  message,
  buttonLabel = 'OK',
  onClose,
  showSuccessIcon = true,
}) {
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div
      className="confirm-modal-backdrop"
      role="presentation"
      onClick={onClose}
    >
      <div
        className="confirm-modal confirm-modal--alert"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        {showSuccessIcon ? (
          <div className="confirm-modal__icon-wrap" aria-hidden>
            <CheckCircleIcon className="confirm-modal__icon" />
          </div>
        ) : null}
        <h2 id={titleId} className="confirm-modal__title confirm-modal__title--with-icon">
          {title}
        </h2>
        {message ? <p className="confirm-modal__message">{message}</p> : null}
        <div className="confirm-modal__actions confirm-modal__actions--center">
          <button
            type="button"
            className="confirm-modal__btn confirm-modal__btn--primary"
            onClick={onClose}
          >
            {buttonLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
