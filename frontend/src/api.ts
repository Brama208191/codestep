import type { ProblemDetail, ProblemSummary, SubmissionResult, SubmissionStatus, SubmissionSummary } from "./types";
import { getClientId } from "./progress";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? body?.detail ?? `Request failed (${response.status})`);
  }
  return response.json();
}

export function fetchProblems(): Promise<ProblemSummary[]> {
  return request("/api/problems");
}

export function fetchProblem(slug: string): Promise<ProblemDetail> {
  return request(`/api/problems/${encodeURIComponent(slug)}`);
}

export function fetchHistory(slug: string): Promise<SubmissionSummary[]> {
  const params = new URLSearchParams({ problem: slug, clientId: getClientId() });
  return request(`/api/submissions?${params}`);
}

interface Accepted {
  id: string;
  status: SubmissionStatus;
}

interface SubmissionView {
  id: string;
  status: SubmissionStatus;
  result: SubmissionResult | null;
}

export async function judge(
  slug: string,
  code: string,
  samplesOnly: boolean,
  onStatus: (status: SubmissionStatus) => void,
): Promise<SubmissionResult> {
  const accepted = await request<Accepted>("/api/submissions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ slug, code, samplesOnly, clientId: getClientId() }),
  });
  onStatus(accepted.status);
  return waitForResult(accepted.id, onStatus);
}

function waitForResult(id: string, onStatus: (status: SubmissionStatus) => void): Promise<SubmissionResult> {
  return new Promise((resolve, reject) => {
    const source = new EventSource(`/api/submissions/${id}/events`);
    let settled = false;

    source.addEventListener("status", (e) => onStatus(JSON.parse((e as MessageEvent).data).status));
    source.addEventListener("result", (e) => {
      settled = true;
      source.close();
      resolve(JSON.parse((e as MessageEvent).data));
    });
    source.onerror = () => {
      if (settled) return;
      source.close();
      poll(id, onStatus).then(resolve, reject);
    };
  });
}

async function poll(id: string, onStatus: (status: SubmissionStatus) => void): Promise<SubmissionResult> {
  for (let attempt = 0; attempt < 120; attempt++) {
    const view = await request<SubmissionView>(`/api/submissions/${id}`);
    onStatus(view.status);
    if (view.result) return view.result;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error("The judge is taking too long. Please try again.");
}
