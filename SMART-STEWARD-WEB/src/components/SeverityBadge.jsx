import { useEffect, useId, useRef, useState } from 'react';
import { InformationCircleIcon } from '@heroicons/react/24/outline';

/**
 * Renders a severity pill and, only when the AI returned a `reason`
 * (`severity_reason` from the model), an info trigger that reveals the
 * model's own justification on hover/focus/tap. No static fallback copy
 * is shown — if the AI didn't produce a rationale, the badge stays plain.
 */
export default function SeverityBadge({ severityKey, label, reason }) {
  const key = severityKey || 'unknown';
  const text = label || 'Not assessed';
  const aiReason = typeof reason === 'string' ? reason.trim() : '';
  const hasAiReason = aiReason.length > 0;

  const [open, setOpen] = useState(false);
  const tooltipId = useId();
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    function handleDocClick(event) {
      if (wrapRef.current && !wrapRef.current.contains(event.target)) {
        setOpen(false);
      }
    }
    function handleKey(event) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', handleDocClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handleDocClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  const handlePointerEnter = (event) => {
    if (event.pointerType !== 'touch') setOpen(true);
  };
  const handlePointerLeave = (event) => {
    if (event.pointerType !== 'touch') setOpen(false);
  };

  return (
    <span className="severity-badge">
      <span className={`severity-badge__pill severity-badge__pill--${key}`}>
        {text}
      </span>
      {hasAiReason ? (
        <span
          ref={wrapRef}
          className="severity-badge__tip-wrap"
          onPointerEnter={handlePointerEnter}
          onPointerLeave={handlePointerLeave}
        >
          <button
            type="button"
            className="severity-badge__info-btn"
            aria-label="Show severity explanation"
            aria-describedby={open ? tooltipId : undefined}
            aria-expanded={open}
            onFocus={() => setOpen(true)}
            onBlur={() => setOpen(false)}
            onClick={() => setOpen((prev) => !prev)}
          >
            <InformationCircleIcon aria-hidden />
          </button>
          <span
            id={tooltipId}
            role="tooltip"
            className={`severity-badge__tooltip ${
              open ? 'severity-badge__tooltip--open' : ''
            }`}
          >
            <span className="severity-badge__tooltip-label">
              Why this severity:
            </span>{' '}
            {aiReason}
          </span>
        </span>
      ) : null}
    </span>
  );
}
