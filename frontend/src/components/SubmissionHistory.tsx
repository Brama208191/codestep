import { useEffect, useState } from "react";
import { fetchHistory } from "../api";
import type { SubmissionSummary } from "../types";
import { VERDICT_LABELS } from "./ResultPanel";

interface Props {
  slug: string;
  refreshKey: number;
  onLoad: (code: string) => void;
}

export default function SubmissionHistory({ slug, refreshKey, onLoad }: Props) {
  const [items, setItems] = useState<SubmissionSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchHistory(slug).then(setItems).catch((e) => setError(e.message));
  }, [slug, refreshKey]);

  if (error) return <p className="notice bad">{error}</p>;
  if (!items) return <p className="muted">Loading…</p>;
  if (items.length === 0) return <p className="muted">No submissions yet. Your attempts will show up here.</p>;

  return (
    <table className="problem-table">
      <thead>
        <tr><th>When</th><th>Result</th><th>Tests</th><th></th></tr>
      </thead>
      <tbody>
        {items.map((s) => (
          <tr key={s.id}>
            <td className="muted">{new Date(s.createdAt).toLocaleString()}</td>
            <td className={s.verdict === "ACCEPTED" ? "verdict-cell good" : "verdict-cell bad"}>
              {s.verdict ? VERDICT_LABELS[s.verdict] : "Judging…"}
              {s.samplesOnly && <span className="muted"> (samples)</span>}
            </td>
            <td className="muted">{s.total > 0 ? `${s.passed}/${s.total}` : ""}</td>
            <td><button onClick={() => onLoad(s.code)}>Load code</button></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
