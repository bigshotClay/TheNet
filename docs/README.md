# TheNet Documentation

This directory contains comprehensive documentation for the TheNet project. TheNet is a fully decentralized, peer-to-peer social platform built with Kotlin Multiplatform.

## 📁 Documentation Structure

### Setup & Getting Started
- [**Setup Guide**](./setup/) - Platform-specific setup instructions
  - [Android Development Setup](./setup/android.md)
  - [iOS Development Setup](./setup/ios.md) 
  - [Desktop Development Setup](./setup/desktop.md)
  - [Development Environment](./setup/environment.md)

### Development Guidelines
- [**Contributing**](./CONTRIBUTING.md) - How to contribute to the project
- [**Coding Standards**](./coding-standards.md) - Code style and conventions
- [**Build & Deployment**](./build-deployment.md) - Build processes and deployment
- [**Troubleshooting**](./troubleshooting.md) - Common issues and solutions

### Architecture & Design
- [**Architecture**](./architecture/) - Technical architecture documentation
  - [System Overview](./architecture/system-overview.md)
  - [Architecture Decision Records](./architecture/adr/) (ADRs)
  - [Component Diagrams](./architecture/diagrams/)
- [**API Documentation**](./api/) - Generated API docs (Dokka)

### Project Management
- [**Epics**](./epics/) - Detailed epic documentation by phase
  - [Phase 1: Core P2P Infrastructure](./epics/phase-1/)
  - [Phase 2: Identity & Blockchain](./epics/phase-2/)
  - [Phase 3: Content & Distribution](./epics/phase-3/)
  - [Phase 4: UI & Polish](./epics/phase-4/)

### Additional Resources
- [**Security**](./security.md) - Security considerations and best practices
- [**Performance**](./performance.md) - Performance guidelines and benchmarks
- [**Testing**](./testing.md) - Testing strategy and guidelines

## 🚀 Quick Navigation

| I want to... | Go here |
|---------------|---------|
| Set up development environment | [Setup Guide](./setup/) |
| Understand the architecture | [System Overview](./architecture/system-overview.md) |
| Contribute code | [Contributing Guide](./CONTRIBUTING.md) |
| Follow coding standards | [Coding Standards](./coding-standards.md) |
| Build and deploy | [Build & Deployment](./build-deployment.md) |
| Troubleshoot issues | [Troubleshooting](./troubleshooting.md) |
| View project phases | [Epic Documentation](./epics/) |

## 📝 Documentation Standards

- All documentation uses Markdown format
- Follow the [Markdown style guide](./coding-standards.md#markdown-style)
- Include code examples where relevant
- Keep documentation up-to-date with code changes
- Use clear, concise language
- Include diagrams for complex concepts

## 🔄 Keeping Documentation Updated

Documentation should be updated as part of development work:

1. **Architecture changes** → Update ADRs and system overview
2. **New features** → Update relevant setup and user guides  
3. **API changes** → Regenerate API documentation
4. **Build changes** → Update build and deployment guides
5. **New dependencies** → Update setup requirements

## 📋 Documentation Tasks

Track documentation tasks in GitHub issues with the `documentation` label. Follow the standard task format `TN-XXX: Documentation task description`.

---

For questions about this documentation, create an issue with the `documentation` label or check the [project discussions](https://github.com/bigshotClay/TheNet/discussions).