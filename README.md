# Shortform

A small Kotlin URL shortener with a browser UI, HTTP API, and SQLite storage.

## Run

Requires JDK 21 or newer. On first run, Gradle downloads the project dependencies.

```bash
./gradlew --project-cache-dir "$HOME/.gradle-project-cache/shortform" run
```

Open [http://localhost:8080](http://localhost:8080). Links are stored in `data/shortener.sqlite` and survive restarts. The Linux-side project cache avoids file-locking issues when the repo is under `/mnt/c` or `/mnt/d` in WSL.

On Windows, use `gradlew.bat run` instead.

## API

Create a short link:

```bash
curl -X POST http://localhost:8080/api/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a-long-page"}'
```

List links and visit counts:

```bash
curl http://localhost:8080/api/links
```

Opening the returned short URL redirects to its destination and increments its visit count. Only valid HTTP and HTTPS destinations are accepted.

Set `PORT` to change the listening port or `SHORTENER_DB_FILE` to choose another SQLite database path.