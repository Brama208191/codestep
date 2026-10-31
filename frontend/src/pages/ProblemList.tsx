import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { fetchProblems } from "../api";
import { getSolved } from "../progress";
import type { ProblemSummary } from "../types";
import SiteBar from "../components/SiteBar";

type View = "path" | "all";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

export default function ProblemList() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<View>("path");
  const [topic, setTopic] = useState("All");
  const solved = useMemo(() => getSolved(), []);

  useEffect(() => {
    fetchProblems().then(setProblems).catch((e) => setError(e.message));
  }, []);

  const topics = useMemo(() => [...new Set(problems.map((p) => p.topic))], [problems]);
  const currentSlug = problems.find((p) => !solved.has(p.slug))?.slug;
  const solvedCount = problems.filter((p) => solved.has(p.slug)).length;

  return (
    <>
      <SiteBar right={problems.length > 0 && <span className="muted">{solvedCount} of {problems.length} solved</span>} />
      <div className="page">
        <section className="hero">
          <h1>Learn the pattern once. Spot it everywhere.</h1>
          <p>Small Java problems, each teaching one idea. Later problems reuse the patterns from earlier ones, so every step builds on the last.</p>
          {problems.length > 0 && (
            <div className="progress">
              <div className="progress-bar"><span style={{ width: `${(solvedCount / problems.length) * 100}%` }} /></div>
              <span>{solvedCount} of {problems.length}</span>
            </div>
          )}
        </section>

        <nav className="tabs">
          <button className={view === "path" ? "tab active" : "tab"} onClick={() => setView("path")}>Path</button>
          <button className={view === "all" ? "tab active" : "tab"} onClick={() => setView("all")}>All problems</button>
        </nav>

        {error && <p className="notice bad">Couldn't load problems: {error}</p>}

        {view === "path" &&
          topics.map((t, topicIndex) => (
            <section key={t} className="chapter">
              <h2><span className="chapter-num">part {topicIndex + 1}</span>{t}</h2>
              <ol className="syllabus">
                {problems.filter((p) => p.topic === t).map((p) => {
                  const isSolved = solved.has(p.slug);
                  const isCurrent = p.slug === currentSlug;
                  const locked = !isSolved && !isCurrent;
                  const status = isSolved ? "solved" : isCurrent ? "current" : "locked";
                  const content = (
                    <>
                      <span className="lesson-num">{pad(p.order)}</span>
                      <span className="lesson-body">
                        <span className="lesson-title">{p.title}</span>
                        <span className="lesson-pattern">{p.pattern}</span>
                      </span>
                      <span className="lesson-status">{isSolved ? "solved" : isCurrent ? "up next" : ""}</span>
                    </>
                  );
                  return (
                    <li key={p.slug} className={`lesson ${status}`}>
                      {locked ? <div className="lesson-row">{content}</div> : <Link to={`/problems/${p.slug}`} className="lesson-row">{content}</Link>}
                    </li>
                  );
                })}
              </ol>
            </section>
          ))}

        {view === "all" && (
          <>
            <label className="filter">
              Topic
              <select value={topic} onChange={(e) => setTopic(e.target.value)}>
                <option>All</option>
                {topics.map((t) => <option key={t}>{t}</option>)}
              </select>
            </label>
            <table className="problem-table">
              <thead>
                <tr><th>#</th><th>Problem</th><th>Pattern</th><th>Topic</th><th></th></tr>
              </thead>
              <tbody>
                {problems.filter((p) => topic === "All" || p.topic === topic).map((p) => (
                  <tr key={p.slug}>
                    <td className="mono muted">{pad(p.order)}</td>
                    <td><Link to={`/problems/${p.slug}`}>{p.title}</Link></td>
                    <td className="muted">{p.pattern}</td>
                    <td className="muted">{p.topic}</td>
                    <td className="solved-mark">{solved.has(p.slug) ? "solved" : ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </>
  );
}
