import logoSrc from '../assets/logo_steward.png';

export default function Logo({ size = 48 }) {
  return (
    <img
      src={logoSrc}
      alt="Smart Steward"
      width={size}
      height={size}
      style={{ objectFit: 'contain' }}
    />
  );
}
