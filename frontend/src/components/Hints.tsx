import { useState } from "react";

const LABELS = ["the idea", "the approach", "pseudocode", "almost there"];

export default function Hints({ hints }: { hints: string[] }) {
  const [revealed, setRevealed] = useState(0);

  return (
    <div className="hints">
      <p className="muted">Give it a go first. Each hint gives away a little more than the one before.</p>
      {hints.slice(0, revealed).map((hint, i) => {
        const text = hint.replace(/^[A-Za-z ]+:\s*/, "");
        return (
          <div key={i} className="hint">
            <div className="hint-label">{LABELS[i] ?? `hint ${i + 1}`}</div>
            {text.includes("\n") ? <pre>{text}</pre> : <p>{text}</p>}
          </div>
        );
      })}
      {revealed < hints.length && (
        <button className="light" onClick={() => setRevealed((r) => r + 1)}>
          Show hint {revealed + 1} of {hints.length}
        </button>
      )}
    </div>
  );
}
