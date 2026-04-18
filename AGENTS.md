# Kestra Template Plugin

## What

- Implements the storage backend under `io.kestra.storage.cloudflare`.
- Includes classes such as `CloudflareFileAttributes`, `CloudflareStorage`, `MetadataUtils`, `CloudflareConfig`.

## Why

- This repository implements a Kestra storage backend for Cloudflare R2.
- It stores namespace files and internal execution artifacts outside local disk.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `templates`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.templates.Example`

### Project Structure

```
plugin-template/
├── src/main/java/io/kestra/plugin/templates/
├── src/test/java/io/kestra/plugin/templates/
├── build.gradle
└── README.md
```

## Local rules

- Keep the scope on Kestra internal storage behavior, not workflow task semantics.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
