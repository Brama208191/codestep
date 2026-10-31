import type { WorkedExample } from "../types";
import RichText from "./RichText";

export default function WorkedExampleView({ example }: { example: WorkedExample }) {
  return (
    <div className="worked-example">
      <p className="muted">A similar problem, fully solved. Read it, then use the same idea on yours.</p>
      <h3>{example.title}</h3>
      <RichText text={example.description} />
      <pre className="code-block">{example.solution}</pre>
      <h4>Why it works</h4>
      <RichText text={example.explanation} />
    </div>
  );
}
