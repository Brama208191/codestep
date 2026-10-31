import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import Editor from "@monaco-editor/react";
import { fetchProblem, judge } from "../api";
import { clearCode, loadCode, markSolved, saveCode } from "../progress";
import type { ProblemDetail, SubmissionResult, SubmissionStatus } from "../types";
import RichText from "../components/RichText";
import Walkthrough from "../components/Walkthrough";
import Hints from "../components/Hints";
import WorkedExampleView from "../components/WorkedExampleView";
import ResultPanel from "../components/ResultPanel";
import SubmissionHistory from "../components/SubmissionHistory";
import SiteBar from "../components/SiteBar";

type Tab = "problem" | "walkthrough" | "example" | "hints" | "history";

export default function ProblemPage() {
  const { slug = "" } = useParams();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [code, setCode] = useState("");
  const [tab, setTab] = useState<Tab>("problem");
  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [status, setStatus] = useState<SubmissionStatus | null>(null);
  const [samplesOnly, setSamplesOnly] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmingReset, setConfirmingReset] = useState(false);
  const [historyKey, setHistoryKey] = useState(0);

  useEffect(() => {
    setProblem(null);
    setResult(null);
    setTab("problem");
    fetchProblem(slug)
      .then((p) => {
        setProblem(p);
        setCode(loadCode(slug) ?? p.starterCode);
      })
      .catch((e) => setError(e.message));
  }, [slug]);

  const running = status !== null;

  async function run(onlySamples: boolean) {
    setSamplesOnly(onlySamples);
    setError(null);
    setResult(null);
    setStatus("QUEUED");
    try {
      const r = await judge(slug, code, onlySamples, setStatus);
      setResult(r);
      if (r.verdict === "ACCEPTED" && !onlySamples) markSolved(slug);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setStatus(null);
      setHistoryKey((k) => k + 1);
    }
  }

  function updateCode(value: string) {
    setCode(value);
    saveCode(slug, value);
  }

  function reset() {
    if (!problem) return;
    clearCode(slug);
    setCode(problem.starterCode);
    setResult(null);
    setError(null);
    setConfirmingReset(false);
  }

  if (!problem) {
    return (
      <>
        <SiteBar />
        <div className="page">{error ? <p className="notice bad">{error}</p> : <p className="muted">Loading…</p>}</div>
      </>
    );
  }

  const tabs: [Tab, string][] = [["problem", "Problem"]];
  if (problem.walkthrough?.length) tabs.push(["walkthrough", "Walkthrough"]);
  if (problem.workedExample) tabs.push(["example", "Worked example"]);
  if (problem.hints.length) tabs.push(["hints", "Hints"]);
  tabs.push(["history", "My submissions"]);

  return (
    <div className="workspace">
      <SiteBar right={<span className="muted">{problem.topic}</span>} />
      <div className="workspace-body">
        <section className="left-pane">
          <div className="meta">
            <span className="pattern-tag">{problem.pattern}</span>
            <span className="badge">{problem.mode === "METHOD" ? "Fill in the method" : "Write the full program"}</span>
          </div>
          <h1>{problem.title}</h1>

          <nav className="tabs">
            {tabs.map(([key, label]) => (
              <button key={key} className={tab === key ? "tab active" : "tab"} onClick={() => setTab(key)}>
                {label}
              </button>
            ))}
          </nav>

          {tab === "problem" && (
            <>
              <RichText text={problem.description} />
              {problem.samples.map((s, i) => (
                <div key={i} className="sample">
                  <div className="io-label">Example {i + 1}</div>
                  <pre>Input:  {s.input}{"\n"}Output: {s.expected}</pre>
                </div>
              ))}
            </>
          )}
          {tab === "walkthrough" && problem.walkthrough && <Walkthrough steps={problem.walkthrough} />}
          {tab === "example" && problem.workedExample && <WorkedExampleView example={problem.workedExample} />}
          {tab === "hints" && <Hints hints={problem.hints} />}
          {tab === "history" && (
            <SubmissionHistory slug={slug} refreshKey={historyKey} onLoad={(c) => { updateCode(c); setTab("problem"); }} />
          )}
        </section>

        <section className="right-pane">
          <div className="editor-card">
            <div className="editor-head">
              <span className="mono">{problem.mode === "METHOD" ? "Solution.java" : "Main.java"}</span>
              <span>{problem.mode === "METHOD" ? "Fill in the method" : "Full program"}</span>
            </div>
            <div className="editor-wrap">
              <Editor
                height="100%"
                language="java"
                theme="chalkboard"
                beforeMount={(monaco) => {
                  monaco.editor.defineTheme("chalkboard", {
                    base: "vs-dark",
                    inherit: true,
                    rules: [
                      { token: "keyword", foreground: "f29fb0" },
                      { token: "string", foreground: "f2d15c" },
                      { token: "number", foreground: "8fd3c1" },
                      { token: "comment", foreground: "7f9187", fontStyle: "italic" },
                      { token: "type", foreground: "8fd3c1" },
                    ],
                    colors: {
                      "editor.background": "#121a16",
                      "editor.foreground": "#ecebe4",
                      "editor.lineHighlightBackground": "#1b2621",
                      "editorLineNumber.foreground": "#4a5a51",
                      "editorLineNumber.activeForeground": "#a9b5ad",
                      "editor.selectionBackground": "#3a4a40",
                      "editorCursor.foreground": "#f2d15c",
                    },
                  });
                }}
                value={code}
                onChange={(value) => updateCode(value ?? "")}
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  fontFamily: "'JetBrains Mono', Consolas, monospace",
                  scrollBeyondLastLine: false,
                  tabSize: 4,
                  padding: { top: 16 },
                  overviewRulerLanes: 0,
                }}
              />
            </div>
            <div className="toolbar">
              {confirmingReset ? (
                <span className="confirm-reset">
                  Erase your code?
                  <button className="danger" onClick={reset}>Yes, reset</button>
                  <button onClick={() => setConfirmingReset(false)}>Cancel</button>
                </span>
              ) : (
                <button onClick={() => setConfirmingReset(true)} disabled={running}>Reset</button>
              )}
              <span className="spacer" />
              <button onClick={() => run(true)} disabled={running}>Run samples</button>
              <button className="primary" onClick={() => run(false)} disabled={running}>Submit</button>
            </div>
          </div>
          <ResultPanel result={result} status={status} error={error} samplesOnly={samplesOnly} />
        </section>
      </div>
    </div>
  );
}
