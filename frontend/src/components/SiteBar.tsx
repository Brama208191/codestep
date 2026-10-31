import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export default function SiteBar({ right }: { right?: ReactNode }) {
  return (
    <header className="site-bar">
      <Link to="/" className="wordmark">codestep</Link>
      <span className="hand tagline">one pattern at a time</span>
      <span className="spacer" />
      {right}
    </header>
  );
}
