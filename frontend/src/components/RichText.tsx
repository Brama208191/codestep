import { Fragment } from "react";

function inline(text: string) {
  return text.split(/(`[^`]+`)/g).map((part, i) =>
    part.startsWith("`") && part.endsWith("`") ? <code key={i}>{part.slice(1, -1)}</code> : <Fragment key={i}>{part}</Fragment>,
  );
}

export default function RichText({ text }: { text: string }) {
  const blocks = text.split(/(```[\s\S]*?```)/g).filter((b) => b.trim() !== "");
  return (
    <div className="rich-text">
      {blocks.map((block, i) =>
        block.startsWith("```") ? (
          <pre key={i}>{block.replace(/^```\w*\n?/, "").replace(/```$/, "")}</pre>
        ) : (
          block
            .split(/\n{2,}/)
            .filter((p) => p.trim() !== "")
            .map((p, j) => <p key={`${i}-${j}`}>{inline(p.trim())}</p>)
        ),
      )}
    </div>
  );
}
