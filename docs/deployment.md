# Deployment on Railway

codestep runs on [Railway](https://railway.com) as two services: one Docker container (the React app, the API and the judge) and a Railway Postgres database. The container sleeps when nobody is using it, so it costs very little.

The multi-container setup in `docker-compose.yml` (nginx, API, Redis, judge workers that start a fresh Docker container per submission) needs the host's Docker socket, which Railway doesn't offer. The `single` profile below is the same code arranged to fit in one container. For a VPS where Docker is available, see [deploy/DEPLOY.md](../deploy/DEPLOY.md).

## Shape of the deployment

```mermaid
flowchart LR
  user([Browser]) -- HTTPS --> edge[Railway edge]
  edge --> api
  subgraph svc [App service, one container]
    api[Spring Boot, user app<br/>serves the React app, REST, SSE]
    queue[in-process queue]
    judge[judged.sh, user judge<br/>empty environment]
    api --> queue --> api
    api -- job over 127.0.0.1:7070 --> judge
  end
  api -- private network --> db[(Railway Postgres)]
  gh[GitHub push] --> ci[CI workflow] -- passes --> build[Railway build] --> svc
```

| Piece | Where it lives |
|---|---|
| Image | [`deploy/railway.Dockerfile`](../deploy/railway.Dockerfile): builds the React app into the API jar and adds the judge (JDK, `run.sh`, `TestRunner`, the security policy) |
| Start script | [`deploy/railway-start.sh`](../deploy/railway-start.sh): turns Railway's `postgresql://` connection string into the API's settings, starts the judge as the `judge` user and the API as the `app` user |
| Judge service | [`runner/judged.sh`](../runner/judged.sh): one `run.sh` per connection, as the `judge` user, listening on `127.0.0.1` only |
| Settings | The `single` profile, [`application-single.yml`](../backend/src/main/resources/application-single.yml): in-process queue instead of Redis, the local judge, one judging thread, only `/actuator/health` exposed |
| Data | Railway Postgres. Flyway creates the tables on the first start |

## First deploy

1. In Railway, open your project (or click **New Project**). The bill is per workspace either way.
2. **Add the database:** click **Create** (or right-click the canvas), then **Database, PostgreSQL**. Railway creates a `Postgres` service with its own volume.
3. **Add the app:** click **Create, GitHub Repo**, and pick the codestep repository. Allow Railway's GitHub app to access it if it asks.
4. **Point Railway at the right Dockerfile.** Open the new service, then **Variables, New Variable**, and add both of these:

   | Name | Value |
   |---|---|
   | `RAILWAY_DOCKERFILE_PATH` | `/deploy/railway.Dockerfile` |
   | `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` |

   The first tells Railway which Dockerfile to build (the repository has several). The second is a reference: Railway fills in the database's private address and password and keeps them up to date, so nothing secret goes into the repository. Railway sets `PORT` itself.
5. Click **Deploy** on the bar at the top of the canvas that says the service has changes. Variable changes only apply to a new deployment; **Restart** reuses the old one.
6. In the service's **Settings**:
   - **Deploy, Healthcheck Path**: `/actuator/health`. A deploy goes live only once the API has started and reached the database.
   - **Deploy, Serverless**: on. The app sleeps after about 5 to 10 minutes without traffic and wakes on the next visit.
   - **Deploy, Restart Policy**: On Failure.
   - **Source, Wait for CI**: on, so a push deploys only after the CI workflow passes.
   - **Networking, Generate Domain**, with port **8080**. You get `https://<name>.up.railway.app`. Put it in the README's **Live demo** line.
7. Optionally turn on **Serverless** for the `Postgres` service too. Once the app is asleep and its connections are closed, the database can sleep as well; the app wakes it on the next request.
8. Open the domain, pick a problem, and press **Submit** on the starter code. The first judgment after waking takes a few seconds longer while the JVMs start.

**Reading the deploy log:** the first line says which database the app is using, without the password (`start: database jdbc:postgresql://postgres.railway.internal:5432/railway`). If it says `DATABASE_URL is not set`, step 4 or 5 is missing.

## What it costs

On the Hobby plan ($5 a month, including $5 of usage), Railway charges $10 per GB of memory per month, $20 per vCPU per month, and $0.15 per GB of volume storage.

| Service | Usage | Approximate cost per month |
|---|---|---|
| App, awake about 10% of the time | ~0.17 GB idle, up to ~0.6 GB while judging | $0.30 to $1.50 |
| Postgres, always on | ~0.1 GB, a small volume | $1 to $1.50 |
| Postgres, with Serverless on | mostly asleep | under $0.50 |

That is about $1 to $3 a month on top of other services in the same workspace. Judging runs one submission at a time (`CODESTEP_WORKER_THREADS=1`), which bounds both memory and CPU. Submissions are capped at 20 per minute per address and 300 per hour for the whole site, so even a determined visitor can keep the judge busy for at most a fraction of one CPU. A usage limit under **Workspace settings, Usage** caps the worst case.

## Why the app can sleep

Railway puts a service to sleep only when it has sent no outbound traffic for a few minutes, and traffic to the database counts. In the `single` profile:

- the connection pool closes idle connections after one minute (`minimum-idle: 0`);
- the queue lives in the process, so there is no Redis to poll every few seconds;
- nothing runs on a timer. The Redis worker's 30-second recovery sweep is replaced by a one-time recovery at start-up: anything left queued or running when the app went to sleep is judged again.

An open problem page keeps no connection, and an SSE stream for a result lasts only until the verdict arrives.

## How the judge is isolated here

The Docker setup gives every submission a fresh container with no network, a read-only filesystem and its own process limits. Railway can't start containers, so the single-container judge gets as close as one container allows, with two independent layers:

1. **Java security policy** ([`runner/runner.policy`](../runner/runner.policy), enforced by `TestRunner`). Submitted classes get no permissions: no files, no network, no processes, no native code, no reflection into the JDK, no turning the policy off. `System.exit` is still caught so the remaining tests run.
2. **Operating-system users.** Submissions run as `judge`, with an empty environment, while only the `app` user's API holds the database password. Linux doesn't let one user read another's process environment or memory, so even code that got past the policy couldn't read the password. `judge` processes are limited to 256 threads, 16 MB files and 120 CPU-seconds each, compilation stops after 20 seconds, and a run stops after its time limit.

What it can't do that Docker does: cut the network at the kernel level (layer 1 does it in Java), or give each submission its own memory cgroup (the heap is capped at 160 MB and judging runs one at a time). Both layers are tested: the judge test suites run against both judges in CI, including attacks that try to read the environment, read files, open sockets, start processes and remove the policy.

## Security headers and limits

Every response carries a Content Security Policy (scripts and workers only from the app itself; the Monaco editor is bundled, not loaded from a CDN), HSTS, `nosniff`, `X-Frame-Options: DENY`, a referrer policy and a permissions policy. The API also does what nginx did in docker-compose: request bodies are capped at 256 KB, submissions are rate limited, and `/problems/...` links are served the app. Database errors are logged in full but answered with a generic message. Metrics (`/actuator/prometheus`) aren't exposed.

Rate limits key on the visitor's address. Railway reports it in `X-Real-IP`; it passes `X-Forwarded-For` through from the client, where anyone can put any address, so the `single` profile reads `X-Real-IP`, and only from the platform's internal proxy. Behind a different proxy, set `CLIENT_IP_HEADER` to the header it sets.

## Operating it

| Task | Where |
|---|---|
| Logs | App service, **Deployments**, then the latest deployment's logs |
| Roll back | **Deployments**, then **Redeploy** on an earlier deployment |
| Look at the data | `Postgres` service, **Data** tab |
| Cost so far this month | Workspace, **Usage** |
| Allow more judging at once | Variable `CODESTEP_WORKER_THREADS=2` (about 0.4 GB more memory while busy) |

## Trying the production image locally

```bash
docker network create cs-net
docker run -d --name cs-db --network cs-net -e POSTGRES_DB=codestep -e POSTGRES_USER=codestep -e POSTGRES_PASSWORD=codestep postgres:16-alpine
docker build -f deploy/railway.Dockerfile -t codestep-railway .
docker run --rm -m 1g --network cs-net -e DATABASE_URL=postgresql://codestep:codestep@cs-db:5432/codestep -p 8080:8080 codestep-railway
```

Open http://localhost:8080.

## What this deployment does not cover

- One instance. The in-process queue doesn't share work between replicas; for more judges, use the docker-compose setup with Redis.
- Weaker isolation than a container per submission, as described above. For a public site that expects hostile traffic, run the Docker judge on a separate VM (see [deploy/DEPLOY.md](../deploy/DEPLOY.md)).
- No backups beyond what Railway's Postgres volume provides. Submissions are practice attempts, not critical data.
