# Sprint 0 acceptance checklist

- [x] Eight Maven modules with one-way dependency boundaries
- [x] Java 21 requirement enforced by Maven and CI
- [x] Spring Boot executable bootstrap module
- [x] MySQL, Redis, Flyway, Actuator, and container configuration
- [x] Twelve-table initial schema with keys needed for scan and credit idempotency
- [x] Standard API response and centralized exception mapping
- [x] Checkstyle, SpotBugs, JaCoCo, and GitHub Actions verification
- [x] README startup, architecture, safety, and roadmap documentation
- [ ] CI run succeeds on GitHub after the initial push
- [ ] `docker compose up --build` smoke test succeeds on a Docker-capable machine

The two unchecked items require execution after this first commit is published. They are verification gates, not missing source files.
