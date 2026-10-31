# codestep

A beginner-friendly practice platform for data structures and algorithms in Java.

Most coding-practice sites assume you already know how to approach a problem. codestep teaches the
**patterns** first: each early problem introduces one reusable idea (best so far, running total,
counting, search and stop early, …), and later problems are built from those same patterns.
Every problem comes with a step-by-step visual walkthrough, a fully solved sibling problem,
progressive hints, and error messages explained in plain language.

## Architecture

```
            ┌──────────────┐   POST /api/submissions      ┌──────────────────┐
 Browser ──►│  React SPA   │ ───────────────────────────► │   API service    │──┐
 (Monaco)   │  (nginx)     │ ◄── SSE: status / result ─── │  Spring Boot     │  │ INSERT (QUEUED)
            └──────────────┘                              └──────────────────┘  ▼
                                                             ▲   │ XADD    ┌────────────┐
                                                 pub/sub     │   ▼         │ PostgreSQL │
                                                 events      │ ┌───────────┴┐ (Flyway)   │
                                                             └─┤   Redis    │────────────┘
                                                               │  Streams   │       ▲
                                                               └─────┬──────┘       │ RUNNING → DONE
                                                         XREADGROUP  ▼              │
                                                          ┌───────────────────────┐ │
                                                          │ Judge worker(s) × N   │─┘
                                                          │ Spring Boot           │
                                                          └──────────┬────────────┘
                                                                     │ docker run -i (tar over stdin)
                                                          ┌──────────▼────────────┐
                                                          │ Sandbox container      │
                                                          │ javac once → one JVM,  │
                                                          │ fresh class loader per │
                                                          │ test                   │
                                                          └────────────────────────┘
```

- **API** (`CODESTEP_WORKER_ENABLED=false`): validates a submission, stores it in Postgres, puts its id
  on a Redis Stream and streams progress back to the browser over Server-Sent Events.
- **Worker** (`CODESTEP_API_ENABLED=false`): reads from the stream as part of a consumer group, judges
  the code in a sandbox, stores the verdict and publishes the result over Redis pub/sub. Add more
  workers to scale horizontally.
- Both roles are **the same Spring Boot jar**, switched by configuration: one codebase to maintain,
  one image to build.

## The sandbox

Every submission runs in a fresh, throwaway container:

| Threat | Defence |
|---|---|
| Network access | `--network none` |
| Writing to disk | `--read-only` root filesystem; only a 64 MB `tmpfs` at `/tmp` |
| Memory bombs | 256 MB container limit, 160 MB JVM heap → *Memory limit exceeded* |
| Fork / thread bombs | `--pids-limit 128` |
| Infinite loops | per-test time limit inside the JVM, plus an overall `timeout -s KILL` and a wall clock in the worker |
| Endless output | stdout capped at 64 KB per test, `--ulimit fsize` |
| Privilege escalation | runs as `nobody`, `--cap-drop ALL`, `no-new-privileges` |
| `System.exit` ending the run early | caught, so the remaining tests still run |
| Static state leaking between tests | each test loads the user's classes in a new class loader |

Code reaches the container as a tar stream on stdin, so the worker never shares a filesystem with
it. For stronger isolation in production, set `JUDGE_RUNTIME=runsc` to run containers under
[gVisor](https://gvisor.dev).

**Prototype trade-off:** the worker controls Docker through the host's Docker socket. Only the
worker has it (the public API does not), but on a shared host this is equivalent to root, so a
production setup should run workers on their own machine or VM.

## Performance

Measured on a laptop (Docker Desktop on Windows) with `find-max` (5 tests), 40 submissions sent at once:

| Workers | Throughput | Judge time p50 / p95 | End-to-end p95 (incl. queueing) |
|---|---|---|---|
| 1 container × 2 threads | 88 submissions/min | 1.33 s / 1.45 s | 27.0 s |
| 2 containers × 4 threads | 151 submissions/min | 1.54 s / 1.72 s | 15.8 s |

Most of the judge time is container start-up plus `javac`. Running every test in **one JVM**
(instead of one JVM per test) made judging independent of the number of tests.

## Common-mistake hints

A generic "wrong answer" doesn't help a beginner much. Test cases can list known mistakes, matched
either by the wrong output or by the exception thrown:

```json
{ "input": "4\n-5 -2 -9 -3\n", "expected": "-2", "mistakes": [
  { "output": "0", "hint": "Every number here is negative, so none of them beats a starting value of 0. Start `biggest` at `nums[0]` instead of 0." }
] }
```

When a submission fails that way, the student sees the targeted hint instead of a generic one.
Mistakes are never sent to the browser, so hidden tests stay hidden.
`CommonMistakesIT` sends 11 realistic buggy beginner solutions through the real sandbox and
checks that each one gets the right hint.

## Problems

Problems are JSON files in [`backend/src/main/resources/problems`](backend/src/main/resources/problems).
Two formats:

- `METHOD`: the user fills in a method of `class Solution`; a hidden harness reads input and calls it.
- `PROGRAM`: the user writes a whole program (any class name, any way of reading input).

Every problem has a reference solution in
[`backend/src/test/resources/solutions`](backend/src/test/resources/solutions). The test suite runs
all of them through the real sandbox, so a broken problem fails the build.

## Tests

```bash
docker build -t codestep-runner:latest runner
cd backend && mvn verify
```

- **Unit tests** (`*Test`): verdict grading, output protocol parsing, main-class detection,
  error explanations, the problem catalogue and the sandbox archive.
- **Integration tests** (`*IT`, Testcontainers):
  - every reference solution is accepted by the real sandbox
  - sandbox security cases (loops, memory, network, filesystem, `System.exit`, static state, output flooding)
  - the full API → queue → worker → Postgres → SSE flow, including the per-user in-flight limit
    under concurrent requests (enforced with a Postgres advisory lock)

GitHub Actions runs all of these, the frontend build and the Docker image builds on every push.

## Running locally

Requires Docker.

```bash
docker compose up --build
```

Open http://localhost:3000.

With metrics dashboards (Prometheus on :9090, Grafana on :3001):

```bash
docker compose --profile monitoring up --build
```

Run more judges:

```bash
docker compose up -d --scale worker=3
```

Frontend with hot reload:

```bash
docker compose up -d --build postgres redis runner api worker
cd frontend && npm install && npm run dev
```

The Vite dev server proxies `/api` to `localhost:8080`, so publish the API port (add `ports: ["8080:8080"]` to the `api` service) when using it.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/problems` | Problem summaries in learning-path order |
| GET | `/api/problems/{slug}` | Problem with samples, hints, worked example and walkthrough |
| POST | `/api/submissions` | `{ slug, code, samplesOnly, clientId }` → `202 { id, status }`; `429` if too many are in flight |
| GET | `/api/submissions/{id}` | Status and result |
| GET | `/api/submissions/{id}/events` | Server-Sent Events: `status`, then `result` |
| GET | `/api/submissions?problem=&clientId=` | Recent submissions for a problem |
| GET | `/actuator/prometheus` | Metrics: judge duration, queue wait, submissions, waiting queue size |

## Deployment

A single small VPS with Caddy for automatic HTTPS: see [deploy/DEPLOY.md](deploy/DEPLOY.md).

## Cost

Everything (Postgres, Redis, API, one worker, nginx) runs within about 1.3 GB of RAM using the memory
limits in `docker-compose.yml`, so it fits on a single small VPS. Grafana and Prometheus are
optional (`--profile monitoring`).
