import { useEffect, useRef } from 'react';
import { XMarkIcon } from '@heroicons/react/24/outline';

export default function MediaLightbox({ open, type = 'image', src = '', alt = '', onClose }) {
  const videoRef = useRef(null);
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  useEffect(() => {
    if (!open || type !== 'video' || !videoRef.current) return;
    const v = videoRef.current;
    v.currentTime = 0;
    const p = v.play();
    if (p && typeof p.catch === 'function') p.catch(() => {});
  }, [open, type, src]);

  useEffect(() => {
    if (!open || !rootRef.current) return undefined;
    const el = rootRef.current;
    if (!document.fullscreenElement && typeof el.requestFullscreen === 'function') {
      const req = el.requestFullscreen();
      if (req && typeof req.catch === 'function') req.catch(() => {});
    }
    return () => {
      if (document.fullscreenElement && typeof document.exitFullscreen === 'function') {
        const exitReq = document.exitFullscreen();
        if (exitReq && typeof exitReq.catch === 'function') exitReq.catch(() => {});
      }
    };
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const onFsChange = () => {
      if (!document.fullscreenElement) onClose?.();
    };
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, [open, onClose]);

  if (!open || !src) return null;

  return (
    <div
      ref={rootRef}
      className="media-lightbox"
      role="dialog"
      aria-modal="true"
      aria-label="Media preview"
    >
      <button
        type="button"
        className="media-lightbox__backdrop"
        aria-label="Close media preview"
        onClick={onClose}
      />
      <div className="media-lightbox__panel">
        <button type="button" className="media-lightbox__close" onClick={onClose} aria-label="Close">
          <XMarkIcon aria-hidden />
        </button>
        {type === 'video' ? (
          <video
            ref={videoRef}
            className="media-lightbox__video"
            src={src}
            controls
            playsInline
            autoPlay
          />
        ) : (
          <img className="media-lightbox__image" src={src} alt={alt} />
        )}
      </div>
    </div>
  );
}
