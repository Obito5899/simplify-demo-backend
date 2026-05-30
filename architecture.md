# Architecture Overview

```mermaid
flowchart LR
  User -->|POST purchase| Simplify[Simplify Money Service]
  Simplify -->|GET rate| Gold[Gold Partner Service]
  Simplify -->|POST pay| Payment[Payment Gateway Service]
  Payment -->|callback/sync| Simplify
  Simplify -->|POST allot| Gold
  Simplify --> Mongo[(MongoDB)]
  Simplify -->|metrics| Prometheus
```

