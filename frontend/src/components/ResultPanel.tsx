import type { SubmissionResult, SubmissionStatus, Verdict } from "../types";
import RichText from "./RichText";

export const VERDICT_LABELS: Record<Verdict, string> = {
  ACCEPTED: "Accepted",
  WRONG_ANSWER: "Not quite yet",
  COMPILE_ERROR: "Java couldn't compile this",
  RUNTIME_ERROR: "Your code crashed",
  TIME_LIMIT_EXCEEDED: "Took too long",
  MEMORY_LIMIT_EXCEEDED: "Used too much memory",
  INTERNAL_ERROR: "Judge error",
};

const STATUS_LABELS: Record<SubmissionStatus, string> = {
  QUEUED: "Waiting for a free judge…",
  RUNNING: "Running your code…",
  DONE: "Finishing up…",
};

interface Props {
  result: SubmissionResult | null;
  status: SubmissionStatus | null;
  error: string | null;
  samplesOnly: boolean;
}

export default function ResultPanel({ result, status, error, samplesOnly }: Props) {
  if (status) return <div className="result-panel empty">{STATUS_LABELS[status]}</div>;
  if (error) return <div className="result-panel"><div className="verdict bad">{error}</div></div>;
  if (!result) return <div className="result-panel empty">Run your code to see results here.</div>;

  const ok = result.verdict === "ACCEPTED";
  const failed = result.failedCase;

  return (
    <div className="result-panel">
      <div className="result-header">
        <span className={`verdict ${ok ? "good" : "bad"}`}>{VERDICT_LABELS[result.verdict]}</span>
        {result.total > 0 && (
          <span>{result.passed} of {result.total} {samplesOnly ? "sample " : ""}tests passed</span>
        )}
        <span className="mono">{result.runtimeMs} ms</span>
      </div>

      {ok && !samplesOnly && <p><span className="hand">nice!</span> Every hidden test passed too.</p>}
      {ok && samplesOnly && <p className="muted">The sample tests pass. Submit to run the hidden tests too.</p>}

      {result.compileOutput && <pre className="error-output">{result.compileOutput}</pre>}

      {failed && (
        <div className="failed-case">
          <div className="muted">Test {failed.number} {failed.sample ? "(sample)" : "(hidden)"}</div>
          <div className="io-grid">
            <div>
              <div className="io-label">Input</div>
              <pre>{failed.input}</pre>
            </div>
            <div>
              <div className="io-label">Expected</div>
              <pre>{failed.expected}</pre>
            </div>
            <div className="mismatch">
              <div className="io-label">You returned</div>
              <pre>{failed.actual === "" ? "(nothing)" : failed.actual}</pre>
            </div>
          </div>
          {failed.stderr && <pre className="error-output">{failed.stderr}</pre>}
        </div>
      )}

      {result.explanation && (
        <div className={result.commonMistake ? "explanation mistake" : "explanation"}>
          <span className="hand">{result.commonMistake ? "common mistake:" : "try this:"}</span>
          <RichText text={result.explanation} />
        </div>
      )}
    </div>
  );
}
