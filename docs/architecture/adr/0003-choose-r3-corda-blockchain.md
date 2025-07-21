# ADR-0003: Choose R3 Corda for Blockchain Implementation

## Status
Accepted

## Context
TheNet requires a blockchain system to provide:

- Immutable identity management and reputation tracking
- Transparent invitation system with audit trails
- Consensus on user verification status
- Decentralized governance and moderation decisions
- Smart contracts for community rules and automated actions
- High throughput for social platform scaling requirements

Key requirements:
- **Performance**: Support for high transaction throughput (social interactions)
- **Privacy**: Selective disclosure of information to relevant parties only
- **Business Logic**: Rich smart contract capabilities for complex social rules
- **JVM Integration**: Seamless integration with Kotlin Multiplatform codebase
- **Enterprise Features**: Professional tooling, support, and governance features
- **Regulated Compliance**: Support for KYC/AML requirements in identity verification

## Decision
We will use R3 Corda as TheNet's blockchain platform, implementing CorDapps (Corda Distributed Applications) for:

- **Identity Management**: User registration, verification status, reputation tracking
- **Invitation System**: Transparent invite issuance, redemption, and revocation
- **Content Governance**: Decentralized moderation decisions and appeals
- **Community Contracts**: Smart contracts for community-specific rules and governance

## Consequences

### Positive Consequences
- **Privacy by Design**: Corda's point-to-point architecture ensures data privacy
- **High Performance**: No global state means better scalability than traditional blockchains
- **Kotlin Integration**: Corda uses Kotlin natively, perfect fit for our tech stack
- **Business Logic**: Rich CorDapp capabilities for complex social platform requirements
- **Enterprise Grade**: Professional tooling, documentation, and enterprise support
- **Regulatory Compliance**: Built-in features for compliance with financial regulations (useful for KYC)
- **Interoperability**: Well-defined interfaces for integration with external systems

### Negative Consequences
- **Complexity**: Corda is complex to set up and operate compared to simpler blockchains
- **Learning Curve**: Team needs extensive Corda-specific knowledge
- **Infrastructure**: Requires running Corda nodes, which adds operational complexity
- **Cost**: Enterprise features and support may require commercial licensing
- **Ecosystem**: Smaller ecosystem compared to Ethereum or other public blockchains

### Risks and Mitigations
- **Risk**: Corda may be overkill for social platform requirements
  - **Mitigation**: Start with simple CorDapps, leverage advanced features as needed
- **Risk**: Node operation complexity may limit adoption
  - **Mitigation**: Provide simplified node deployment, consider managed hosting options
- **Risk**: Corda's focus on financial services may not align with social networking
  - **Mitigation**: Focus on identity and governance use cases where Corda excels

## Alternatives Considered

### Ethereum
**Pros**: 
- Large ecosystem and community
- Mature smart contract platform
- Extensive tooling and documentation
- Public blockchain with built-in consensus

**Cons**: 
- High gas costs for frequent social interactions
- Public visibility of all transactions (privacy concerns)
- Proof of Work energy consumption (though moving to Proof of Stake)
- Solidity language different from our Kotlin stack

### Hyperledger Fabric
**Pros**: 
- Enterprise-focused permissioned blockchain
- Good privacy features with channels
- Strong identity management capabilities

**Cons**: 
- Go/Java ecosystem, less aligned with Kotlin
- Complex setup and configuration
- Limited smart contract capabilities compared to Corda
- More focused on supply chain than social applications

### Polygon/Layer 2 Solutions
**Pros**: 
- Lower transaction costs than Ethereum mainnet
- Faster transaction processing
- Ethereum compatibility

**Cons**: 
- Still public blockchain with privacy limitations
- Additional complexity of Layer 2 bridge management
- Dependent on Ethereum ecosystem limitations

### Custom Blockchain
**Pros**: 
- Complete control over consensus and features
- Optimized specifically for social networking requirements
- No external dependencies or limitations

**Cons**: 
- 12+ months development time for basic blockchain
- Security risks without extensive peer review
- No existing ecosystem or tooling
- Significant ongoing maintenance burden

## Implementation Notes

### CorDapp Architecture
```kotlin
// Identity Management CorDapp
@BelongsToContract(UserContract::class)
data class UserState(
    val userId: UserId,
    val publicKey: PublicKey,
    val verificationStatus: VerificationStatus,
    val invitedBy: UserId?,
    val createdAt: Instant,
    override val participants: List<AbstractParty>
) : ContractState

class UserContract : Contract {
    companion object {
        const val ID = "com.bigshotsoftware.thenet.contracts.UserContract"
    }
    
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Register -> verifyRegistration(tx)
            is Commands.Verify -> verifyUserVerification(tx)
            is Commands.UpdateReputation -> verifyReputationUpdate(tx)
        }
    }
}

// Invitation System CorDapp
@BelongsToContract(InvitationContract::class)
data class InvitationState(
    val invitationId: InvitationId,
    val inviter: UserId,
    val inviteeEmail: String,
    val status: InvitationStatus,
    val expiresAt: Instant,
    override val participants: List<AbstractParty>
) : ContractState
```

### Node Architecture
- **Network Map**: Corda network map service for node discovery
- **Identity Service**: Corda identity service integrated with Hyperledger Identus
- **Bootstrap Nodes**: Initial network nodes operated by TheNet foundation
- **User Nodes**: Optional personal nodes for power users (privacy/control)

### Integration with P2P Layer
- Corda provides consensus and immutable record keeping
- IPv8 P2P layer provides real-time communication and content distribution
- Blockchain state changes trigger P2P updates
- P2P layer validates blockchain state before processing messages

### Privacy Implementation
- Use Corda's point-to-point architecture to limit data visibility
- Implement selective disclosure for verification status
- Encrypt sensitive data in state objects
- Use Corda's confidential identities for enhanced privacy

### Performance Considerations
- **Transaction Batching**: Batch related social actions into single transactions
- **State Evolution**: Design states for efficient querying and updates  
- **Network Topology**: Optimize node connections for social graph topology
- **Caching**: Cache frequently accessed blockchain state locally

### Development Workflow
1. **CorDapp Development**: Develop contracts and flows in Kotlin
2. **Testing**: Use Corda's MockNetwork for integration testing
3. **Deployment**: Deploy CorDapps to test network, then production
4. **Monitoring**: Monitor node health, transaction throughput, network connectivity

### Migration Strategy
If Corda proves inadequate:
1. Abstract blockchain layer behind interfaces
2. Implement state migration utilities for moving to alternative platform
3. Design bridge contracts for gradual migration
4. Maintain backwards compatibility during transition period

### Operational Considerations
- **Node Management**: Automated deployment and monitoring of Corda nodes
- **Network Governance**: Clear governance process for network upgrades
- **Backup/Recovery**: Comprehensive backup strategy for node data
- **Security**: Secure key management and network access controls

This decision provides TheNet with enterprise-grade blockchain capabilities while leveraging our Kotlin expertise and providing the privacy and performance characteristics required for a social platform.