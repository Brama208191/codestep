export type Mode = "METHOD" | "PROGRAM";

export interface ProblemSummary {
  slug: string;
  title: string;
  topic: string;
  pattern: string;
  order: number;
  difficulty: number;
  mode: Mode;
}

export interface WorkedExample {
  title: string;
  description: string;
  solution: string;
  explanation: string;
}

export interface TraceStep {
  array: (number | string)[];
  pointers: Record<string, number> | null;
  highlight: number[] | null;
  vars: Record<string, number | string> | null;
  note: string;
}

export interface SampleCase {
  input: string;
  expected: string;
}

export interface ProblemDetail {
  slug: string;
  title: string;
  topic: string;
  pattern: string;
  difficulty: number;
  mode: Mode;
  description: string;
  starterCode: string;
  hints: string[];
  workedExample: WorkedExample | null;
  walkthrough: TraceStep[] | null;
  samples: SampleCase[];
}

export type Verdict =
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "COMPILE_ERROR"
  | "RUNTIME_ERROR"
  | "TIME_LIMIT_EXCEEDED"
  | "MEMORY_LIMIT_EXCEEDED"
  | "INTERNAL_ERROR";

export interface FailedCase {
  number: number;
  sample: boolean;
  input: string;
  expected: string;
  actual: string;
  stderr: string;
}

export type SubmissionStatus = "QUEUED" | "RUNNING" | "DONE";

export interface SubmissionSummary {
  id: string;
  verdict: Verdict | null;
  status: SubmissionStatus;
  samplesOnly: boolean;
  passed: number;
  total: number;
  createdAt: string;
  code: string;
}

export interface SubmissionResult {
  verdict: Verdict;
  passed: number;
  total: number;
  failedCase: FailedCase | null;
  compileOutput: string | null;
  explanation: string | null;
  commonMistake: boolean;
  runtimeMs: number;
}
