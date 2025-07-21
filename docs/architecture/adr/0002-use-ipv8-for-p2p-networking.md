# ADR-0002: Use IPv8 for P2P Networking

## Status
Accepted

## Context
TheNet requires a robust P2P networking layer for decentralized communication without relying on central servers. The networking layer must support:

- Peer discovery and connection management
- NAT traversal for home/corporate networks  
- Message routing and relay capabilities
- Resilience against network partitions
- Security against Sybil attacks and message tampering
- Mobile-friendly operation (battery efficiency, intermittent connectivity)
- Cross-platform compatibility (Android, iOS, Desktop)

Key requirements:
- Decentralized peer discovery (no bootstrap servers long-term)
- Automatic NAT traversal (STUN/TURN-like capabilities)
- Message authentication and integrity
- Efficient message routing and flooding prevention
- Community resilience and attack resistance
- Academic research backing for security properties

## Decision
We will use IPv8 as the foundation for TheNet's P2P networking layer, with Kotlin implementation and platform-specific optimizations.

IPv8 provides:
- **Peer Discovery**: Distributed hash table (DHT) for decentralized peer finding
- **NAT Traversal**: Built-in hole punching and relay mechanisms
- **Community Management**: Isolated communities with separate routing
- **Security**: Built-in message authentication and anti-spam mechanisms
- **Research Foundation**: Developed at Delft University with academic backing
- **Mobile Optimization**: Designed for battery-efficient mobile operation

## Consequences

### Positive Consequences
- **Proven Architecture**: IPv8 is battle-tested in Tribler BitTorrent client (10+ years)
- **Academic Foundation**: Research-backed security and performance properties
- **NAT Traversal**: Sophisticated hole punching without external STUN/TURN servers
- **Anti-Spam**: Built-in mechanisms against flooding and Sybil attacks
- **Community Isolation**: Natural support for isolated social graphs
- **Mobile-First**: Designed for intermittent connectivity and battery efficiency
- **Kotlin Implementation**: Available as pure Kotlin library for multiplatform use

### Negative Consequences
- **Complexity**: IPv8 is complex to configure and tune properly
- **Documentation**: Limited documentation compared to more mainstream P2P libraries
- **Ecosystem**: Smaller ecosystem compared to libp2p or BitTorrent DHT
- **Learning Curve**: Team needs to understand IPv8-specific concepts and patterns
- **Debugging**: P2P networking issues can be challenging to debug and reproduce

### Risks and Mitigations
- **Risk**: IPv8 Kotlin implementation may have bugs or performance issues
  - **Mitigation**: Contribute back to IPv8 Kotlin project, maintain our own fork if needed
- **Risk**: Small community may mean limited support and updates
  - **Mitigation**: Build internal expertise, consider contributing to IPv8 development
- **Risk**: IPv8 may not scale to TheNet's target user base
  - **Mitigation**: Implement gradual rollout, have migration plan to alternative P2P solution

## Alternatives Considered

### libp2p
**Pros**: 
- Large ecosystem and active development
- Used by IPFS, Filecoin, Ethereum 2.0
- Comprehensive protocol suite
- Good documentation and tooling

**Cons**: 
- Primarily Go/JavaScript implementations (no mature Kotlin version)
- More complex than needed for TheNet's use case  
- Designed for blockchain networks, not social networking
- Less focus on mobile/battery optimization

### BitTorrent DHT (Mainline DHT)
**Pros**: 
- Extremely well-tested and scalable
- Simple and efficient
- Available in many languages

**Cons**: 
- No built-in NAT traversal beyond basic techniques
- Limited security features (no message authentication)
- No community isolation mechanisms
- Designed for file sharing, not social networking

### Custom P2P Protocol
**Pros**: 
- Complete control over features and optimization
- Tailored specifically for TheNet's requirements
- No external dependencies or limitations

**Cons**: 
- Significant development time (6+ months)
- High risk of security vulnerabilities
- No community vetting of protocol design
- Extensive testing required across network conditions

### Hypercore Protocol (Dat/Holepunch)
**Pros**: 
- Modern P2P protocol designed for applications
- Good performance and scalability
- Active development and community

**Cons**: 
- Primarily JavaScript/Node.js ecosystem
- Limited mobile optimization
- Less mature than IPv8 for social networking use cases
- No existing Kotlin implementation

## Implementation Notes

### IPv8 Integration Architecture
```kotlin
// Core IPv8 Integration
class P2PNetworkManager(
    private val ipv8Community: Community,
    private val messageHandler: MessageHandler
) {
    suspend fun startNetwork() {
        ipv8Community.bootstrap()
        ipv8Community.startDiscovery()
    }
    
    suspend fun sendMessage(peerId: PeerId, message: ByteArray) {
        ipv8Community.sendMessage(peerId, message)
    }
}

// TheNet-specific Community
class TheNetCommunity : Community() {
    override fun onMessage(peer: Peer, payload: ByteArray) {
        // Handle TheNet-specific message types
        when (val message = parseMessage(payload)) {
            is PostMessage -> handlePost(peer, message)
            is InviteMessage -> handleInvite(peer, message) 
            is ProfileUpdate -> handleProfileUpdate(peer, message)
        }
    }
}
```

### Platform-Specific Optimizations

#### Android
- Use Android's NetworkInfo for connection state awareness
- Implement battery optimization-friendly background processing
- Leverage Android's VPN APIs for advanced NAT traversal if needed

#### iOS
- Use iOS Network framework for low-level networking
- Implement background app refresh for maintaining connections
- Integrate with iOS networking stack for optimal performance

#### Desktop
- Use full IPv8 feature set without mobile constraints
- Implement desktop-specific discovery mechanisms (mDNS, UPnP)
- Provide detailed networking diagnostics and configuration

### Security Considerations
- All messages must be cryptographically signed
- Implement message deduplication and flood protection
- Use IPv8's built-in anti-Sybil mechanisms
- Add application-layer rate limiting and reputation systems

### Performance Tuning
- Configure IPv8 DHT parameters for social network topology
- Optimize message routing for typical social graph structures
- Implement intelligent peer selection based on social distance
- Use IPv8's community isolation to prevent cross-contamination

### Testing Strategy
1. **Unit Tests**: Test IPv8 integration components in isolation
2. **Integration Tests**: Test multi-peer scenarios in controlled environments
3. **Network Tests**: Test across various network conditions (NAT, firewall, mobile)
4. **Load Tests**: Verify scalability with increasing numbers of peers
5. **Security Tests**: Verify resistance to common P2P attacks

### Migration Strategy
If IPv8 proves inadequate, migration plan:
1. Abstract P2P layer behind interfaces to enable replacement
2. Implement message format versioning for protocol evolution
3. Design fallback mechanisms for peer discovery
4. Plan gradual migration with backwards compatibility

### Community Contribution
- Contribute improvements back to IPv8 Kotlin project
- Document TheNet-specific IPv8 usage patterns
- Share performance optimizations with IPv8 community
- Collaborate on mobile-specific improvements

This decision provides TheNet with a solid, research-backed P2P networking foundation while maintaining the flexibility to optimize and extend as needed.