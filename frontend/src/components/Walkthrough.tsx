import { useEffect, useState } from "react";
import type { TraceStep } from "../types";

export default function Walkthrough({ steps }: { steps: TraceStep[] }) {
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const step = steps[index];
  const last = steps.length - 1;

  useEffect(() => {
    if (!playing) return;
    if (index >= last) {
      setPlaying(false);
      return;
    }
    const timer = setTimeout(() => setIndex((i) => i + 1), 1500);
    return () => clearTimeout(timer);
  }, [playing, index, last]);

  const pointers = Object.entries(step.pointers ?? {});
  const highlight = new Set(step.highlight ?? []);

  return (
    <div className="walkthrough">
      {step.array.length > 0 && (
        <div className="cells">
          {step.array.map((value, i) => {
            const active = pointers.some(([, p]) => p === i);
            return (
              <div key={i} className="cell-column">
                <div className={`cell ${highlight.has(i) ? "highlight" : ""} ${active ? "active" : ""}`}>{value}</div>
                <div className="cell-index">{i}</div>
                <div className="cell-pointers">
                  {pointers.filter(([, p]) => p === i).map(([name]) => (
                    <span key={name} className="pointer">{name}</span>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {step.vars && Object.keys(step.vars).length > 0 && (
        <div className="vars">
          {Object.entries(step.vars).map(([name, value]) => (
            <span key={name} className="var">
              {name} = <strong>{value}</strong>
            </span>
          ))}
        </div>
      )}

      <p className="step-note">{step.note}</p>

      <div className="walk-controls">
        <button onClick={() => { setPlaying(false); setIndex((i) => Math.max(0, i - 1)); }} disabled={index === 0}>Back</button>
        {index === last ? (
          <button className="light" onClick={() => { setPlaying(false); setIndex(0); }}>Start over</button>
        ) : (
          <button className="light" onClick={() => { setPlaying(false); setIndex((i) => i + 1); }}>Next step</button>
        )}
        <button onClick={() => { if (index >= last) setIndex(0); setPlaying((p) => !p); }}>
          {playing ? "Pause" : "Play"}
        </button>
        <div className="step-dots" aria-label={`Step ${index + 1} of ${steps.length}`}>
          {steps.map((_, i) => <span key={i} className={i <= index ? "done" : ""} />)}
        </div>
      </div>
    </div>
  );
}
