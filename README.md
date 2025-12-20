# Ergo Node – Full and Thin Execution Modes

This repo is the official Ergo node with a new **thin** execution mode. Thin mode keeps the public surface identical (HTTP APIs, Swagger, P2P, mining coordination, wallet UX) while delegating heavy validation to a remote Validation Core.

## What’s here
- Full node (unchanged) and thin node (new) in one binary.
- Validation abstraction with pluggable backends:
  - `LocalValidationBackend` (existing full-node behavior).
  - `RemoteValidationBackend` (gRPC stub; fail-closed).
- Wallet modes: `delegated | filtered | full` (delegated is default in thin).
- Operator visibility endpoints: `GET /node/mode`, `GET /node/validation/status`.

## Execution modes
- `full` (default): legacy behavior; local UTXO, scripts, mempool, block assembly.
- `thin`: keeps networking, headers, fork choice, wallet/keystore, signing, mining coordination, and tx broadcast local; delegates UTXO, script execution, tx validation, mempool, and block template construction to a remote Validation Core.

Mandatory thin-mode guardrails (startup fails if violated):
- `ergo.node.verifyTransactions = true`
- `ergo.node.verifyScripts = true`
- `ergo.node.stateType = digest`
- `ergo.node.validation.endpoint` must be set
- If mining in thin mode: `ergo.node.useExternalMiner = true`

## Quick start (thin)
1) Build (uses bundled launcher if you don’t have sbt installed):
```
java -jar sbt-launch.jar assembly
```
Artifact: `target/scala-2.12/ergo-6.0.1-0-21ca898f-20251220-1605-SNAPSHOT.jar`

2) Configure a validation endpoint and thin mode. A baseline `thin.conf` is provided:
```
ergo {
  node {
    execution {
      mode = thin
      validation.endpoint = "grpc://validation-core:9053"
    }
    wallet.mode = delegated
    verifyTransactions = true
    verifyScripts = true
    stateType = digest
    useExternalMiner = true
  }
}
```
3) Run (mainnet example):
```
java -jar target/scala-2.12/ergo-6.0.1-0-21ca898f-20251220-1605-SNAPSHOT.jar --mainnet -c thin.conf
```
Swap `--testnet` as needed.

## Transaction flow (thin)
1) Build unsigned tx locally.  
2) Fetch input context from Validation Core.  
3) Remote validate via Validation Core.  
4) Sign locally (keys never leave node).  
5) Broadcast via P2P.

## Mining (thin)
- Block templates are requested from the Validation Core through the validation backend.
- Stratum coordination and job handling stay local.
- External miner required (`useExternalMiner = true` when `execution.mode = thin`).

## Wallet modes
- `delegated` (default in thin): wallet-relevant data fetched from Validation Core; no full block download.
- `filtered`: downloads blocks and filters locally for wallet scanning.
- `full`: legacy full-wallet behavior.

## APIs
- All existing endpoints and Swagger schemas remain unchanged.
- Added read-only:
  - `GET /node/mode` → execution mode, wallet mode, backend in use, enforced settings.
  - `GET /node/validation/status` → backend health, last success, rolling/last latency, endpoint.

## Why run thin vs a normal node
- Lower resource footprint: delegate UTXO, scripts, mempool, and block assembly to a remote core; keep only headers/state digest locally.
- Fast startup and sync: header sync only; no full blocks by default in `delegated` wallet mode.
- Operational continuity: public APIs, Swagger, mining coordination, and wallet UX stay identical.
- Security preserved: scripts/txs still verified (remotely), signing stays local; forced `verifyTransactions`, `verifyScripts`, and `stateType=digest`.
- Mining-friendly: still serve block templates and Stratum coordination while outsourcing heavy validation.
- Auditability: explicit remote-validation flow with health/latency visibility via `/node/validation/status`.

## Validation Core interface (gRPC, stubbed client)
- Methods: `ValidateTransaction`, `GetInputContext`, `SubmitTransaction`, `GetMempoolInfo`, `BuildBlockTemplate` (and optional `GetCapabilities`).
- All requests include `protocolVersion`.
- Thin node fails closed if backend is unavailable; no silent fallback to local validation.

## Development notes
- Code lives primarily under `src/main/scala/org/ergoplatform/nodeView/validation/*` and settings under `src/main/scala/org/ergoplatform/settings/*`.
- `thin.conf` is an overlay; `application.conf` remains the base.
- No consensus-rule changes; state type is forced to digest only in thin mode.
- Logging banners surface enforced settings and privacy implications of delegated wallet mode.

## Testing
- Unit/property tests: `java -jar sbt-launch.jar test`
- Integration tests (Docker required): `sudo sbt it:test`
- Bootstrapping tests (long-running, Docker): `sudo sbt it2:test`

## More info
- Docs and papers: https://docs.ergoplatform.com/
- Contributing: https://docs.ergoplatform.com/contribute/
- Community: Discord (#development) and https://t.me/ErgoDevelopers
