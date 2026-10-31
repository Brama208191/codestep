# Deploying codestep to a VPS

Everything runs on one small Linux server with Docker. Caddy handles HTTPS automatically.

**Cost:** a 2 vCPU / 4 GB server (e.g. Hetzner CX22, about €4–5/month, or a $6–12 droplet elsewhere)
plus a domain. Only ports 22, 80 and 443 are open. Postgres, Redis, the API and the worker are
reachable only inside Docker's private network.

## 1. Server and domain

1. Create an Ubuntu 24.04 server and add your SSH key.
2. Point your domain at it with an `A` record (e.g. `codestep.yourname.dev → <server IP>`).

## 2. Prepare the server

SSH in as root, then:

```bash
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

Docker bypasses `ufw` for ports it publishes. That's fine here, because the production setup
publishes only Caddy's 80 and 443.

Then turn off password logins for SSH: set `PasswordAuthentication no` in `/etc/ssh/sshd_config`
and run `systemctl restart ssh`.

## 3. Get the code and configure it

```bash
git clone https://github.com/<you>/codestep.git /opt/codestep
cd /opt/codestep
cp .env.example .env
nano .env
```

Set `DOMAIN` to your domain and `POSTGRES_PASSWORD` to a long random value
(`openssl rand -base64 32` makes one).

## 4. Start it

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

The first build takes a few minutes. Caddy gets a TLS certificate as soon as the domain resolves
to the server. Open `https://<your domain>`.

Check it:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f api worker
```

## 5. Nightly backups

```bash
chmod +x /opt/codestep/deploy/backup.sh
(crontab -l 2>/dev/null; echo "0 3 * * * /opt/codestep/deploy/backup.sh >> /var/log/codestep-backup.log 2>&1") | crontab -
```

Backups are kept for 14 days in `/var/backups/codestep`. Copy them off the server now and then
(for example `scp` to your machine, or your provider's snapshot feature).

Restore:

```bash
gunzip -c /var/backups/codestep/<file>.sql.gz | \
  docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres psql -U codestep -d codestep
```

## 6. Updating

```bash
cd /opt/codestep
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Flyway applies database migrations automatically when the API starts.

## Hardening options

- **gVisor:** install `runsc` and set `JUDGE_RUNTIME=runsc` in `.env` to run every submission
  under a user-space kernel.
- **Separate judge machine:** the worker controls Docker on its host. To keep that away from the
  database, run the worker on a second server pointed at this one's Postgres and Redis over a
  private network.
- **Monitoring:** `--profile monitoring` starts Prometheus and Grafana. Their ports (9090 and
  3001) are blocked by the firewall, so reach them through an SSH tunnel:
  `ssh -L 3001:localhost:3001 root@<server>`.

## Abuse protection already built in

- each browser can have at most 3 submissions being judged at once
- each IP can submit 20 times per minute (burst of 10), enforced by nginx
- when 100 submissions are already queued, new ones are rejected with "busy, try again"
- every submission runs in a locked-down container (see the README's sandbox section)
