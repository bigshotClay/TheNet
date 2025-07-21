# TheNet

> **Fully decentralized, peer-to-peer social platform built with Kotlin Multiplatform**

TheNet is a censorship-resistant, user-centric social platform where users own their data through blockchain technology and P2P distribution. No central servers, no corporate gatekeepers.

## 🎯 Vision

- **User Sovereignty**: Content lives on users' devices and their followers' devices
- **Censorship Resistance**: No single points of failure or control
- **Trust & Accountability**: Verified identity system with privacy preservation
- **Community by Connection**: Natural discourse through follow relationships

## 🏗️ Architecture

- **Language**: Kotlin Multiplatform (Android, iOS, Desktop)
- **P2P Networking**: IPv8 with NAT traversal
- **Blockchain**: R3 Corda with Kotlin CorDapps
- **Identity**: Hyperledger Identus + zkPass (privacy-preserving KYC)
- **Content Distribution**: Nabu (Java IPFS) + ttorrent
- **UI**: Compose Multiplatform
- **Real-time**: libp2p GossipSub

## 🚀 Development Status

| Phase | Status | Duration |
|-------|--------|----------|
| Phase 1: Core P2P Infrastructure | 🚧 Planning | Months 1-3 |
| Phase 2: Identity & Blockchain | ⏳ Upcoming | Months 4-6 |
| Phase 3: Content & Distribution | ⏳ Upcoming | Months 7-9 |
| Phase 4: UI & Polish | ⏳ Upcoming | Months 10-12 |

## 📋 Current Focus

**Phase 1 Epics in Progress:**
- [ ] Epic 1.1: Development Environment Setup
- [ ] Epic 1.2: IPv8 P2P Networking Integration  
- [ ] Epic 1.3: Corda Blockchain Foundation
- [ ] Epic 1.4: Anti-Bot Infrastructure

## 🛠️ Development Setup

### Prerequisites
- JDK 17+
- Kotlin 1.9.22+
- Android Studio (latest stable)
- Xcode 15+ (for iOS)
- Docker (for test networks)

### Quick Start
```bash
git clone git@github.com:bigshotClay/TheNet.git
cd TheNet

# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run Android app
./gradlew :android:installDebug
```

## 📚 Documentation

- [Development Plan](./DEVELOPMENT_PLAN.md) - Complete phase-by-phase roadmap
- [CLAUDE.md](./CLAUDE.md) - Project context and implementation guide
- [Architecture Docs](./docs/architecture/) - Technical architecture details
- [Epic Docs](./docs/epics/) - Detailed epic documentation

## 🤝 Contributing

1. Check the [current milestones](https://github.com/bigshotClay/TheNet/milestones) and [project boards](https://github.com/bigshotClay/TheNet/projects)
2. Pick up a task from the "Ready" column
3. Create a branch: `feature/TN-XXX-task-description`
4. Work on the task following acceptance criteria
5. Submit a PR with reference to the task: `TN-XXX: Description`

## 📊 Project Tracking

- **Issues**: Use task templates for consistent tracking
- **Projects**: Phase-based kanban boards
- **Milestones**: Monthly development targets
- **Labels**: Automated phase and epic labeling

## 🔒 Security

- All user actions cryptographically signed
- Private keys never leave user devices
- Regular security audits planned
- Report security issues to: security@thenet.app

## 📄 License

[To be determined - likely Apache 2.0 or MIT for maximum compatibility]

## 🌟 Features

### User Model
- **Anonymous**: Text posts, comments, emojis, basic profile
- **Verified**: Media posts, content forwarding, invite issuance (max 10 lifetime)

### Core Capabilities
- Invite-only onboarding with verification
- Immutable content versioning
- BitTorrent-style media distribution
- Emoji reaction microblogs
- Content preservation and archiving
- Real-time updates via GossipSub

---

**Status**: 🚧 Early Development | **Target**: Beta launch in 12 months

For questions, check the [discussions](https://github.com/bigshotClay/TheNet/discussions) or [project boards](https://github.com/bigshotClay/TheNet/projects).