# Rights indexer ABI baseline

Decoder version `rwa-9adaba8-v1` is pinned to `StuartCHAN/ip-breaker-rwa@9adaba8`.
The backend topic catalog is implemented in `ContractEventDecoder`; it deliberately does not load
`frontend/src/abis.ts`, because that UI ABI omits events. Before an address is activated in
`chain_contract`, compile the pinned Solidity commit, compare the event signatures and record the
artifact SHA-256 and deployed runtime code hash in `deployment-manifest.json`.
