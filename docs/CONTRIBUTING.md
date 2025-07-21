# Contributing to TheNet

Welcome to TheNet! We're excited that you're interested in contributing to a truly decentralized social platform. This guide will help you get started with contributing to the project.

## 🎯 Project Vision

TheNet is building a censorship-resistant, user-centric social platform where:
- Users own their data through blockchain technology and P2P distribution
- No central servers or corporate gatekeepers control the network
- Community governance and moderation happens transparently
- Privacy and security are built-in, not afterthoughts

## 🚀 Getting Started

### Prerequisites
Before contributing, ensure you have:
1. Read the [Development Environment Setup](./setup/environment.md)
2. Reviewed the [Coding Standards](./coding-standards.md)
3. Understood the [Architecture Overview](./architecture/system-overview.md)
4. Set up your development environment for your target platform(s)

### First-Time Setup
```bash
# 1. Fork the repository on GitHub
# 2. Clone your fork
git clone git@github.com:YOUR_USERNAME/TheNet.git
cd TheNet

# 3. Add upstream remote
git remote add upstream git@github.com:bigshotClay/TheNet.git

# 4. Install dependencies and verify setup
./gradlew build
./gradlew test
```

## 📋 How to Contribute

### 1. Pick a Task

#### Current Development Focus
We're currently working on **Phase 1: Core P2P Infrastructure**. Check our:
- [GitHub Issues](https://github.com/bigshotClay/TheNet/issues) for available tasks
- [Project Boards](https://github.com/bigshotClay/TheNet/projects) for current sprint work
- [Milestones](https://github.com/bigshotClay/TheNet/milestones) for upcoming targets

#### Task Types
- 🐛 **Bug Fixes**: Issues labeled `bug`
- ✨ **Features**: Issues labeled `enhancement` or `feature`
- 📚 **Documentation**: Issues labeled `documentation`  
- 🧪 **Testing**: Issues labeled `testing`
- 🏗️ **Architecture**: Issues labeled `architecture`
- 🔧 **DevOps**: Issues labeled `devops`

### 2. Follow the Development Workflow

#### CRITICAL: Ticket-Scoped Development
Every development session MUST follow this workflow (see [CLAUDE.md](../CLAUDE.md) for details):

1. **Get Assignment**: Never start without a specific GitHub ticket assignment
2. **Update Ticket**: Mark ticket as "in-progress" and add start comment
3. **Work Within Scope**: Only implement features within the ticket's acceptance criteria
4. **Track Progress**: Update ticket with progress every 30 minutes of work
5. **Complete Properly**: Mark ticket complete with summary when done

```bash
# Start development session
gh issue edit TN-XXX --add-label "in-progress"
gh issue comment TN-XXX --body "🚧 Development started by @username"

# During development - commit with ticket reference
git commit -m "TN-XXX: Implement feature Y per acceptance criteria

- Added specific functionality
- Updated tests
- Addresses acceptance criteria: specific criteria"

# Complete session
gh issue edit TN-XXX --add-label "completed" --remove-label "in-progress"
gh issue comment TN-XXX --body "✅ Development complete - ready for review"
```

#### Branch Strategy
```bash
# Create feature branch
git checkout -b feature/TN-XXX-brief-description

# Keep branch up to date
git fetch upstream
git rebase upstream/main

# Push branch
git push origin feature/TN-XXX-brief-description
```

### 3. Development Process

#### Code Quality Requirements
Before submitting any PR:

```bash
# Run all quality checks
./gradlew detektAll ktlintCheckAll testAll

# Auto-fix formatting issues  
./gradlew ktlintFormat

# Check specific module
./gradlew :shared:test :shared:detekt
```

#### Testing Requirements
- **Unit Tests**: Required for all business logic
- **Integration Tests**: Required for P2P networking and blockchain integration
- **UI Tests**: Required for user-facing features
- **Security Tests**: Required for cryptographic and networking code

```bash
# Run comprehensive tests
./gradlew testAll

# Run module-specific tests
./gradlew :p2p:test
./gradlew :blockchain:test
```

#### Documentation Updates
When contributing, update relevant documentation:
- Code comments for complex logic
- API documentation for public interfaces
- Architecture documentation for design changes
- Setup guides for new dependencies
- Troubleshooting guides for known issues

### 4. Submit Pull Request

#### PR Preparation Checklist
- [ ] All tests pass locally
- [ ] Code follows project standards (detekt + ktlint pass)
- [ ] Commit messages reference ticket (TN-XXX format)
- [ ] PR description explains the change
- [ ] Documentation updated if needed
- [ ] No secrets or credentials in code
- [ ] Security implications considered

#### PR Template
```markdown
## Description
Brief description of changes made.

## Related Issue
Closes #TN-XXX

## Type of Change
- [ ] Bug fix
- [ ] New feature  
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Security
- [ ] No hardcoded secrets
- [ ] Input validation added
- [ ] Security implications reviewed

## Screenshots (if applicable)
[Add screenshots for UI changes]
```

### 5. Code Review Process

#### What Reviewers Look For
- **Correctness**: Does the code solve the problem correctly?
- **Security**: Are there any security vulnerabilities?
- **Performance**: Is the code efficient for P2P networking requirements?
- **Maintainability**: Is the code readable and well-structured?
- **Testing**: Are there adequate tests?
- **Documentation**: Is the code documented appropriately?

#### Responding to Review Feedback
- Address all reviewer comments
- Ask questions if feedback is unclear
- Make requested changes in separate commits
- Update the PR description if scope changes

## 🏗️ Architecture Guidelines

### Module Organization
TheNet is organized into focused modules:

```
shared/           # Common business logic
├── blockchain/   # Blockchain integration
├── content/      # Content management
├── core/         # Core utilities
├── identity/     # Identity management
├── network/      # P2P networking
└── ui/          # Shared UI components

platform modules/
├── android/      # Android app
├── desktop/      # Desktop app
├── ios/          # iOS app (future)
├── blockchain/   # Corda CorDapps
├── content/      # Content distribution
├── identity/     # Identity services
├── p2p/          # P2P networking
└── ui/           # Compose UI components
```

### Design Principles
- **Separation of Concerns**: Each module has a single responsibility
- **Dependency Inversion**: Depend on abstractions, not concretions
- **Testability**: All components should be easily testable
- **Platform Abstraction**: Use expect/actual for platform-specific code
- **Security First**: All sensitive operations must be secure by design

### Key Patterns
- **Repository Pattern**: For data access abstraction
- **Use Cases**: For business logic encapsulation
- **Dependency Injection**: For loose coupling
- **State Management**: Unidirectional data flow
- **Error Handling**: Result types for expected failures

## 🔐 Security Guidelines

### Cryptography
- Never implement custom cryptography
- Use established libraries (Bouncy Castle, LazySodium)
- All user actions must be cryptographically signed
- Private keys never leave user devices
- Use secure key storage (Keystore/Keychain)

### Input Validation
```kotlin
// Always validate inputs
fun createPost(content: String, userId: UserId): Result<Post> {
    return try {
        require(content.length <= MAX_POST_LENGTH) { "Post too long" }
        require(userId.isValid()) { "Invalid user ID" }
        require(!content.containsMaliciousContent()) { "Invalid content" }
        
        Result.success(Post.create(content.sanitize(), userId))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }
}
```

### Secrets Management
- Never commit secrets, keys, or credentials
- Use environment variables or secure key storage
- Rotate keys regularly
- Use different keys for different environments

## 🌐 Platform-Specific Contributions

### Android Development
- Follow Android architecture guidelines
- Use Android-specific security features (Keystore)
- Test on multiple Android versions and devices
- Consider battery optimization and background processing

### iOS Development (Future)
- Follow Apple Human Interface Guidelines
- Use iOS-specific security features (Keychain)
- Test on multiple iOS versions and devices
- Consider app review guidelines compliance

### Desktop Development
- Support Windows, macOS, and Linux
- Follow platform-specific UI conventions
- Test across different desktop environments
- Consider system integration features

## 📊 Performance Guidelines

### P2P Networking
- Minimize message overhead
- Implement efficient routing algorithms
- Handle network partitions gracefully
- Optimize for mobile battery life

### Blockchain Operations
- Batch related transactions
- Cache frequently accessed data
- Minimize on-chain storage
- Use off-chain solutions where possible

### UI Performance
- Use lazy loading for large lists
- Implement proper state management
- Optimize recomposition in Compose
- Test on low-end devices

## 🧪 Testing Guidelines

### Test Structure
```kotlin
@Test
fun `should create user when valid data provided`() = runTest {
    // Arrange
    val userData = ValidUserData.sample()
    val userService = UserService(mockRepository)
    
    // Act
    val result = userService.createUser(userData)
    
    // Assert
    assertTrue(result.isSuccess)
    verify(mockRepository).saveUser(any())
}
```

### Testing Requirements
- **Unit Tests**: Test individual functions and classes
- **Integration Tests**: Test module interactions
- **Contract Tests**: Test API contracts between modules
- **Security Tests**: Test cryptographic operations
- **Performance Tests**: Test under load conditions

### Mock Usage
- Mock external dependencies (network, storage, crypto)
- Use real objects for simple data classes
- Verify important interactions
- Don't over-mock (avoid testing mocks)

## 📚 Documentation Standards

### Code Documentation
```kotlin
/**
 * Manages P2P connections for TheNet network.
 * 
 * @param maxConnections Maximum concurrent connections
 * @param discoveryService Service for finding peers
 */
class P2PNetworkManager(
    private val maxConnections: Int,
    private val discoveryService: PeerDiscoveryService
) {
    /**
     * Connects to a peer and returns the connection.
     * 
     * @param peerId Target peer identifier
     * @param timeout Connection timeout in milliseconds
     * @return Connection result or error
     */
    suspend fun connectToPeer(peerId: PeerId, timeout: Long): Result<Connection>
}
```

### Markdown Documentation
- Use clear headings and structure
- Include code examples where helpful
- Keep documentation up-to-date with code changes
- Use diagrams for complex concepts

## 💬 Community

### Communication Channels
- **GitHub Discussions**: For general questions and ideas
- **GitHub Issues**: For bug reports and feature requests
- **Pull Requests**: For code review and collaboration

### Code of Conduct
We are committed to providing a welcoming and inclusive environment:
- Be respectful and constructive in all interactions
- Focus on technical merits of contributions
- Welcome newcomers and help them succeed
- Follow the [Contributor Covenant](https://www.contributor-covenant.org/)

### Getting Help
- **Technical Questions**: Create a GitHub Discussion
- **Bug Reports**: Create a GitHub Issue with reproduction steps
- **Feature Ideas**: Create a GitHub Discussion first, then an Issue
- **Security Issues**: Email security@thenet.app (to be created)

## 🏆 Recognition

### Types of Contributions
We value all types of contributions:
- Code contributions (features, bug fixes)
- Documentation improvements
- Testing and quality assurance
- Bug reports and issue triage
- Architecture and design discussions
- Community support and mentoring

### Contributor Recognition
- Contributors listed in project README
- Significant contributors invited to core team
- Annual contributor appreciation
- Conference speaking opportunities for major contributors

## 🚀 Advanced Contributing

### Becoming a Core Contributor
Core contributors have additional responsibilities:
- Code review responsibilities
- Architecture decision participation
- Mentoring new contributors
- Release management duties

**Requirements for Core Contributor Status:**
- Consistent high-quality contributions over 6+ months
- Deep understanding of project architecture
- Positive community interactions
- Commitment to project goals and timeline

### Release Process Participation
Major contributors can participate in:
- Beta testing and validation
- Release notes preparation
- Community communication
- Post-release support

---

## 🎉 Thank You!

Thank you for contributing to TheNet! Every contribution, no matter how small, helps build a more decentralized and user-empowered internet.

**Together, we're building the future of social networking.** 🌐

---

For questions about contributing, create a GitHub Discussion or check our [project documentation](./README.md).