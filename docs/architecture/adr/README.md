# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records for TheNet project. ADRs document important architectural decisions, their context, and consequences.

## 📋 ADR Index

### Core Architecture
- [ADR-0001: Adopt Kotlin Multiplatform](./0001-adopt-kotlin-multiplatform.md)
- [ADR-0002: Use IPv8 for P2P Networking](./0002-use-ipv8-for-p2p-networking.md)
- [ADR-0003: Choose R3 Corda for Blockchain](./0003-choose-r3-corda-blockchain.md)
- [ADR-0004: Implement Hyperledger Identus for DIDs](./0004-implement-hyperledger-identus-dids.md)
- [ADR-0005: Use Compose Multiplatform for UI](./0005-use-compose-multiplatform-ui.md)

### System Design
- [ADR-0006: Content Distribution Strategy](./0006-content-distribution-strategy.md)
- [ADR-0007: Invitation System Design](./0007-invitation-system-design.md)
- [ADR-0008: Anti-Bot Mechanisms](./0008-anti-bot-mechanisms.md)

### Technical Decisions  
- [ADR-0009: Cryptography Libraries Selection](./0009-cryptography-libraries-selection.md)
- [ADR-0010: Data Storage Strategy](./0010-data-storage-strategy.md)

## 📝 ADR Template

Use this template for new ADRs:

```markdown
# ADR-XXXX: [Title]

## Status
[Proposed | Accepted | Deprecated | Superseded by ADR-YYYY]

## Context
[What is the issue motivating this decision or change?]

## Decision
[What is the change we're proposing or have agreed to implement?]

## Consequences
[What becomes easier or more difficult to do and any risks introduced by this change?]

## Alternatives Considered
[What other options were evaluated?]

## Implementation Notes
[Any specific implementation details or requirements]
```

## 🔄 ADR Process

### Creating a New ADR
1. Copy the template above
2. Assign the next sequential number (XXXX)
3. Fill in all sections thoroughly
4. Set status to "Proposed"
5. Create a pull request for review
6. Update status to "Accepted" when approved

### Updating Existing ADRs
- ADRs should rarely be changed after acceptance
- If an ADR needs significant changes, create a new ADR that supersedes it
- Update the old ADR's status to "Superseded by ADR-YYYY"

### ADR Review Process
1. Technical accuracy review by core team
2. Impact assessment on existing systems
3. Implementation feasibility evaluation
4. Alignment with project goals verification

---

ADRs help maintain architectural consistency and provide context for future decisions. All significant architectural decisions should be documented as ADRs.