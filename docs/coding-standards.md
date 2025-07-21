# Coding Standards & Conventions

Comprehensive coding standards for TheNet project. All contributors must follow these guidelines to ensure code consistency, maintainability, and quality.

## 🎯 General Principles

### Code Quality Goals
1. **Readability**: Code should be self-documenting and easy to understand
2. **Maintainability**: Changes should be easy to make without breaking existing functionality
3. **Performance**: Code should be efficient and scalable for P2P networking
4. **Security**: All code must follow security best practices
5. **Testability**: Code should be designed for easy testing

### Design Principles
- **SOLID Principles**: Follow Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion
- **DRY**: Don't Repeat Yourself
- **KISS**: Keep It Simple, Stupid
- **YAGNI**: You Aren't Gonna Need It
- **Composition over Inheritance**: Prefer composition when possible

## 📝 Kotlin Standards

### Naming Conventions

#### Classes and Interfaces
```kotlin
// ✅ Good: PascalCase
class UserProfileManager
interface P2PNetworkProvider
sealed class ContentEvent

// ❌ Bad: incorrect casing
class userProfileManager
interface p2pNetworkProvider
```

#### Functions and Variables
```kotlin
// ✅ Good: camelCase
fun calculateMessageHash(): String
val currentUserProfile: UserProfile
private var isNetworkConnected: Boolean

// ❌ Bad: incorrect casing
fun CalculateMessageHash(): String
val current_user_profile: UserProfile
```

#### Constants
```kotlin
// ✅ Good: SCREAMING_SNAKE_CASE
const val MAX_MESSAGE_LENGTH = 280
const val P2P_DEFAULT_PORT = 8080
const val BLOCKCHAIN_TIMEOUT_MS = 30_000L

// ❌ Bad: incorrect casing
const val maxMessageLength = 280
const val p2pDefaultPort = 8080
```

#### Packages
```kotlin
// ✅ Good: lowercase with dots
package com.bigshotsoftware.thenet.p2p.networking
package com.bigshotsoftware.thenet.blockchain.contracts

// ❌ Bad: mixed case
package com.bigshotsoftware.theNet.P2P.Networking
```

### Code Structure

#### Class Organization
```kotlin
class UserManager(
    private val networkProvider: P2PNetworkProvider,
    private val cryptoService: CryptographyService
) {
    // 1. Companion object (if needed)
    companion object {
        const val MAX_USERS = 10_000
    }
    
    // 2. Properties (public first, then private)
    val activeUsers: List<User> get() = _activeUsers.toList()
    
    private val _activeUsers = mutableListOf<User>()
    private var lastSyncTime = 0L
    
    // 3. Init blocks
    init {
        require(networkProvider.isAvailable()) { 
            "Network provider must be available" 
        }
    }
    
    // 4. Public methods
    suspend fun addUser(user: User): Result<Unit> {
        // Implementation
    }
    
    // 5. Private methods
    private fun validateUser(user: User): Boolean {
        // Implementation
    }
    
    // 6. Nested classes/interfaces (if needed)
    data class UserStats(val totalUsers: Int, val activeUsers: Int)
}
```

#### Function Structure
```kotlin
// ✅ Good: Clear, single responsibility
suspend fun sendMessage(
    recipient: UserId,
    content: String,
    metadata: MessageMetadata = MessageMetadata.default()
): Result<MessageId> {
    // Validate input
    require(content.length <= MAX_MESSAGE_LENGTH) {
        "Message content exceeds maximum length of $MAX_MESSAGE_LENGTH"
    }
    require(recipient.isValid()) { "Recipient ID is invalid" }
    
    return try {
        // Create and sign message
        val message = createMessage(recipient, content, metadata)
        val signedMessage = cryptoService.signMessage(message)
        
        // Send via P2P network
        val messageId = networkProvider.sendMessage(signedMessage)
        
        // Store locally
        messageStorage.store(signedMessage)
        
        Result.success(messageId)
    } catch (e: Exception) {
        logger.error("Failed to send message to $recipient", e)
        Result.failure(e)
    }
}

// ❌ Bad: Too complex, multiple responsibilities
fun processUserAction(action: String, data: Any?) {
    // Complex logic mixing validation, processing, and side effects
}
```

### Error Handling

#### Use Result Type for Recoverable Errors
```kotlin
// ✅ Good: Use Result for expected failures
suspend fun connectToPeer(peerId: PeerId): Result<Connection> {
    return try {
        val connection = networkLayer.connect(peerId)
        Result.success(connection)
    } catch (e: NetworkException) {
        Result.failure(e)
    }
}

// Usage
when (val result = connectToPeer(peerId)) {
    is Result.Success -> handleConnection(result.value)
    is Result.Failure -> logger.warn("Connection failed", result.exception)
}
```

#### Use Exceptions for Programming Errors
```kotlin
// ✅ Good: Use exceptions for programming errors
fun processMessage(message: Message?) {
    requireNotNull(message) { "Message cannot be null" }
    require(message.isValid()) { "Message failed validation" }
    
    // Process message
}
```

### Coroutines and Concurrency

#### Structured Concurrency
```kotlin
// ✅ Good: Proper scope management
class MessageProcessor(
    private val scope: CoroutineScope
) {
    suspend fun processMessages(messages: List<Message>) = withContext(Dispatchers.IO) {
        messages.map { message ->
            async { processMessage(message) }
        }.awaitAll()
    }
}

// ❌ Bad: Global scope usage
class MessageProcessor {
    fun processMessage(message: Message) {
        GlobalScope.launch { // Don't do this!
            // Processing logic
        }
    }
}
```

#### Cancellation Support
```kotlin
// ✅ Good: Cancellation-aware
suspend fun syncWithBlockchain() {
    while (isActive) { // Check for cancellation
        try {
            val updates = blockchainService.fetchUpdates()
            processUpdates(updates)
            delay(SYNC_INTERVAL_MS) // Cancellable delay
        } catch (e: CancellationException) {
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            logger.error("Sync failed", e)
            delay(ERROR_RETRY_DELAY_MS)
        }
    }
}
```

### Null Safety and Type Safety

#### Prefer Non-Null Types
```kotlin
// ✅ Good: Non-null by default
data class UserProfile(
    val userId: UserId,
    val displayName: String,
    val avatar: ImageUrl? = null // Only nullable when needed
)

// ❌ Bad: Unnecessary nullability
data class UserProfile(
    val userId: UserId?,
    val displayName: String?,
    val avatar: ImageUrl?
)
```

#### Safe Null Handling
```kotlin
// ✅ Good: Safe null handling
fun displayUserName(user: User?): String {
    return user?.profile?.displayName ?: "Anonymous"
}

// ✅ Good: Early return for null checks
fun processUser(user: User?) {
    user ?: return
    
    // Process non-null user
}

// ❌ Bad: Force unwrapping
fun displayUserName(user: User?): String {
    return user!!.profile!!.displayName // Don't do this!
}
```

## 🏗️ Architecture Standards

### Module Organization

#### Dependency Direction
```
UI Layer (android, ios, desktop)
    ↓
Presentation Layer (shared/ui)
    ↓
Domain Layer (shared/domain)
    ↓
Data Layer (shared/data)
    ↓
Platform Layer (p2p, blockchain, identity, content)
```

#### Module Boundaries
```kotlin
// ✅ Good: Clear module interfaces
// In :domain module
interface UserRepository {
    suspend fun getUser(userId: UserId): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
}

// In :data module
class UserRepositoryImpl(
    private val localDataSource: UserLocalDataSource,
    private val networkDataSource: UserNetworkDataSource
) : UserRepository {
    // Implementation
}
```

### Dependency Injection

#### Use Constructor Injection
```kotlin
// ✅ Good: Constructor injection
class MessageService(
    private val cryptoService: CryptographyService,
    private val networkService: NetworkService,
    private val storage: MessageStorage
) {
    // Clear dependencies, easy to test
}

// ❌ Bad: Service locator pattern
class MessageService {
    private val cryptoService = ServiceLocator.getCryptoService()
    private val networkService = ServiceLocator.getNetworkService()
}
```

#### Interface Segregation
```kotlin
// ✅ Good: Focused interfaces
interface MessageSender {
    suspend fun sendMessage(message: Message): Result<MessageId>
}

interface MessageReceiver {
    fun subscribeToMessages(): Flow<Message>
}

// ❌ Bad: God interface
interface MessageService {
    suspend fun sendMessage(message: Message): Result<MessageId>
    fun subscribeToMessages(): Flow<Message>
    suspend fun deleteMessage(messageId: MessageId): Result<Unit>
    suspend fun editMessage(messageId: MessageId, newContent: String): Result<Unit>
    fun getMessageHistory(): Flow<List<Message>>
    suspend fun markAsRead(messageId: MessageId): Result<Unit>
    // ... 20 more methods
}
```

## 🧪 Testing Standards

### Test Structure

#### AAA Pattern (Arrange, Act, Assert)
```kotlin
@Test
fun `sendMessage should return success when message is valid`() = runTest {
    // Arrange
    val recipient = UserId("user123")
    val content = "Hello, TheNet!"
    val messageService = MessageService(mockCryptoService, mockNetworkService)
    
    // Act
    val result = messageService.sendMessage(recipient, content)
    
    // Assert
    assertTrue(result.isSuccess)
    verify(mockNetworkService).sendMessage(any())
}
```

#### Test Naming
```kotlin
// ✅ Good: Descriptive test names
@Test
fun `connectToPeer should return failure when network is unavailable`()

@Test
fun `verifyMessage should return false when signature is invalid`()

@Test
fun `getUserProfile should return cached data when network request fails`()

// ❌ Bad: Unclear test names
@Test
fun testConnect()

@Test
fun testMessage()
```

### Mock Usage
```kotlin
// ✅ Good: Mock external dependencies
class MessageServiceTest {
    private val mockNetworkService = mockk<NetworkService>()
    private val mockCryptoService = mockk<CryptographyService>()
    
    @Test
    fun `should handle network timeout gracefully`() = runTest {
        // Given
        coEvery { mockNetworkService.sendMessage(any()) } throws NetworkTimeoutException()
        
        // When
        val result = messageService.sendMessage(recipient, content)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkTimeoutException)
    }
}
```

## 📱 UI Standards

### Compose Multiplatform

#### State Management
```kotlin
// ✅ Good: Proper state management
@Composable
fun MessageList(
    messages: List<Message>,
    onMessageClick: (MessageId) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(
                message = message,
                onClick = { onMessageClick(message.id) }
            )
        }
    }
}

// ❌ Bad: State management issues
@Composable
fun MessageList() {
    var messages by remember { mutableStateOf(listOf<Message>()) }
    
    // Direct state updates in composable
    LaunchedEffect(Unit) {
        messages = fetchMessages() // Don't do this!
    }
}
```

#### Reusable Components
```kotlin
// ✅ Good: Reusable, configurable component
@Composable
fun TheNetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = when (variant) {
                ButtonVariant.Primary -> MaterialTheme.colors.primary
                ButtonVariant.Secondary -> MaterialTheme.colors.secondary
            }
        )
    ) {
        Text(text)
    }
}
```

## 🔐 Security Standards

### Cryptographic Operations

#### Key Management
```kotlin
// ✅ Good: Secure key handling
class CryptographyService(
    private val keyStorage: SecureKeyStorage
) {
    suspend fun signMessage(message: Message): SignedMessage {
        val privateKey = keyStorage.getPrivateKey()
        // Use privateKey, then clear from memory
        return try {
            cryptoProvider.sign(message.content, privateKey)
        } finally {
            privateKey.clear() // Clear sensitive data
        }
    }
}

// ❌ Bad: Insecure key handling
class CryptographyService {
    private val privateKey = "hardcoded-private-key" // Don't do this!
    
    fun signMessage(message: String): String {
        return sign(message, privateKey) // Key always in memory
    }
}
```

#### Input Validation
```kotlin
// ✅ Good: Thorough validation
fun createUser(
    username: String,
    email: String
): Result<User> {
    return try {
        // Validate inputs
        require(username.isValidUsername()) { "Invalid username format" }
        require(email.isValidEmail()) { "Invalid email format" }
        require(!username.containsMaliciousContent()) { "Username contains invalid content" }
        
        val user = User(
            username = username.sanitize(),
            email = email.sanitize()
        )
        Result.success(user)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }
}

// ❌ Bad: No validation
fun createUser(username: String, email: String): User {
    return User(username, email) // Direct usage without validation
}
```

## 📊 Performance Standards

### Efficient Collections
```kotlin
// ✅ Good: Appropriate collection types
class MessageCache {
    private val recentMessages = LinkedHashMap<MessageId, Message>(16, 0.75f, true)
    private val messagesByUser = mutableMapOf<UserId, MutableList<MessageId>>()
    
    fun addMessage(message: Message) {
        recentMessages[message.id] = message
        messagesByUser.computeIfAbsent(message.senderId) { mutableListOf() }
            .add(message.id)
    }
}

// ❌ Bad: Inefficient operations
class MessageCache {
    private val messages = mutableListOf<Message>()
    
    fun findMessage(id: MessageId): Message? {
        return messages.find { it.id == id } // O(n) lookup
    }
}
```

### Lazy Initialization
```kotlin
// ✅ Good: Lazy initialization
class P2PNetworkManager {
    private val connectionPool by lazy { 
        ConnectionPool(maxConnections = 100)
    }
    
    private val peerDiscovery by lazy {
        PeerDiscoveryService(networkConfig)
    }
}
```

## 🔍 Code Quality Tools

### Detekt Configuration
Detekt rules are configured in `config/detekt/detekt.yml`:

```yaml
# Key rules for TheNet
complexity:
  CyclomaticComplexMethod:
    threshold: 15
  LongMethod:
    threshold: 60
  
naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
  ClassNaming:
    classPattern: '[A-Z][A-Za-z0-9]*'
    
security:
  HardcodedCredential: true
  WeakCryptography: true
```

### KtLint Configuration
```kotlin
// .editorconfig
[*.{kt,kts}]
indent_size = 4
insert_final_newline = true
max_line_length = 120
```

## 📝 Documentation Standards

### KDoc Comments
```kotlin
/**
 * Manages P2P connections for TheNet network.
 * 
 * This service handles peer discovery, connection establishment,
 * and message routing across the decentralized network.
 * 
 * @property maxConnections Maximum number of concurrent peer connections
 * @property discoveryService Service for finding peers on the network
 * 
 * @sample com.bigshotsoftware.thenet.samples.P2PNetworkSample
 * @since 1.0.0
 */
class P2PNetworkManager(
    private val maxConnections: Int = 50,
    private val discoveryService: PeerDiscoveryService
) {
    
    /**
     * Connects to a specific peer by their ID.
     * 
     * @param peerId Unique identifier of the peer to connect to
     * @param timeout Connection timeout in milliseconds
     * @return [Result] containing the connection on success, or error on failure
     * 
     * @throws IllegalArgumentException if peerId is invalid
     * @throws NetworkException if connection fails due to network issues
     */
    suspend fun connectToPeer(
        peerId: PeerId, 
        timeout: Long = 30_000L
    ): Result<Connection> {
        // Implementation
    }
}
```

### Markdown Style Guide

#### Headers
```markdown
# Main Title (H1) - Only one per document
## Section Headers (H2) - Major sections  
### Subsection Headers (H3) - Subsections
#### Detail Headers (H4) - Specific details
```

#### Code Blocks
```markdown
Use language-specific code blocks:

```kotlin
fun example() {
    println("Hello, TheNet!")
}
```

```bash
./gradlew build
```
```

#### Lists and Tables
```markdown
Ordered lists for sequential steps:
1. First step
2. Second step
3. Third step

Unordered lists for related items:
- Feature A
- Feature B  
- Feature C

Tables for structured data:
| Column 1 | Column 2 | Column 3 |
|----------|----------|----------|
| Value 1  | Value 2  | Value 3  |
```

## ✅ Code Review Checklist

### Before Submitting PR
- [ ] All tests pass (`./gradlew testAll`)
- [ ] Code follows naming conventions
- [ ] No hardcoded secrets or credentials
- [ ] Error handling is appropriate
- [ ] Documentation is updated
- [ ] No TODO comments left in production code
- [ ] Performance implications considered
- [ ] Security implications reviewed

### During Code Review
- [ ] Code is readable and self-documenting
- [ ] Business logic is correct
- [ ] Error scenarios are handled
- [ ] Tests cover the happy path and edge cases
- [ ] No code duplication
- [ ] Appropriate abstractions used
- [ ] Security best practices followed

## 🔄 Enforcement

### Automated Checks
```bash
# Run all quality checks before committing
./gradlew detektAll ktlintCheckAll testAll

# Auto-fix formatting issues
./gradlew ktlintFormat
```

### Pre-commit Hooks
Consider setting up pre-commit hooks to automatically run quality checks:

```bash
#!/bin/sh
# .git/hooks/pre-commit
./gradlew detekt ktlintCheck
if [ $? -ne 0 ]; then
    echo "Code quality checks failed. Please fix issues before committing."
    exit 1
fi
```

---

**Following these standards ensures high-quality, maintainable, and secure code across the TheNet project.** 

For questions about these standards, create an issue with the `coding-standards` label or discuss in the project [discussions](https://github.com/bigshotClay/TheNet/discussions).