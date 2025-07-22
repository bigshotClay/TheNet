# Bootstrap Node Deployment Guide

## Overview

Bootstrap nodes are critical infrastructure components in TheNet's P2P network. They serve as initial contact points for new peers joining the network and help with peer discovery. This guide covers deployment, configuration, and maintenance of bootstrap nodes.

## Architecture

### Bootstrap Node Components

1. **BootstrapNode**: Enhanced node representation with health tracking
2. **BootstrapNodeManager**: Manages node lifecycle and selection
3. **BootstrapNodeSelector**: Implements selection strategies (round-robin, priority, proximity)
4. **BootstrapHealthMonitor**: Monitors node health and collects metrics
5. **BootstrapFallbackStrategy**: Handles failures with multiple fallback mechanisms
6. **BootstrapService**: High-level service integrating all components

### Node Selection Strategies

- **Priority-based**: Selects nodes based on configured priority and success rate
- **Round-robin**: Distributes load evenly across available nodes
- **Proximity-based**: Prefers nodes in the same geographic region
- **Composite**: Combines multiple strategies for optimal selection

### Fallback Mechanisms

1. **Exponential Backoff**: Retries with increasing delays
2. **DNS Discovery**: Queries DNS SRV records for additional nodes
3. **Hardcoded Emergency Nodes**: Last-resort fallback nodes
4. **Peer Exchange**: Gets bootstrap nodes from connected peers

## Deployment Requirements

### Hardware Requirements

- **CPU**: 2+ cores (4+ recommended)
- **RAM**: 4GB minimum (8GB recommended)
- **Storage**: 50GB SSD
- **Network**: 100Mbps+ symmetric connection
- **Ports**: TCP 8090 (configurable)

### Software Requirements

- Java 17+ runtime
- Docker (optional, for containerized deployment)
- SSL certificates for secure communication

## Deployment Steps

### 1. Server Setup

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Java 17
sudo apt install openjdk-17-jre-headless -y

# Create service user
sudo useradd -r -s /bin/false thenet

# Create directories
sudo mkdir -p /opt/thenet/bootstrap
sudo mkdir -p /var/log/thenet
sudo chown -R thenet:thenet /opt/thenet /var/log/thenet
```

### 2. Bootstrap Node Configuration

Create `/opt/thenet/bootstrap/config.yaml`:

```yaml
bootstrap:
  # Node identification
  nodeId: "bootstrap1.thenet.io"
  region: "us-east"
  priority: 100
  
  # Network configuration
  host: "0.0.0.0"
  port: 8090
  publicHost: "bootstrap1.thenet.io"
  
  # Performance tuning
  maxConnections: 10000
  connectionTimeout: 5000
  
  # Security
  enableTls: true
  tlsCertPath: "/opt/thenet/certs/server.crt"
  tlsKeyPath: "/opt/thenet/certs/server.key"
  
  # Health monitoring
  healthCheckEndpoint: "/health"
  metricsEndpoint: "/metrics"
  
  # Peer management
  maxPeersPerIp: 10
  blacklistDuration: 3600
  
  # Resource limits
  maxMemory: "4G"
  maxCpu: "200%"
```

### 3. Systemd Service

Create `/etc/systemd/system/thenet-bootstrap.service`:

```ini
[Unit]
Description=TheNet Bootstrap Node
After=network.target

[Service]
Type=simple
User=thenet
Group=thenet
WorkingDirectory=/opt/thenet/bootstrap

ExecStart=/usr/bin/java \
  -Xms2G \
  -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dconfig.file=/opt/thenet/bootstrap/config.yaml \
  -jar /opt/thenet/bootstrap/thenet-bootstrap.jar

Restart=always
RestartSec=10

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/log/thenet

# Resource limits
LimitNOFILE=65536
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
```

### 4. Network Configuration

```bash
# Configure firewall
sudo ufw allow 8090/tcp
sudo ufw allow 22/tcp
sudo ufw enable

# Configure sysctl for high connections
cat << EOF | sudo tee /etc/sysctl.d/99-thenet.conf
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.core.netdev_max_backlog = 65535
EOF

sudo sysctl -p /etc/sysctl.d/99-thenet.conf
```

### 5. SSL/TLS Setup

```bash
# Generate self-signed certificate (for testing)
sudo mkdir -p /opt/thenet/certs
cd /opt/thenet/certs

sudo openssl req -x509 -nodes -days 365 -newkey rsa:4096 \
  -keyout server.key -out server.crt \
  -subj "/C=US/ST=State/L=City/O=TheNet/CN=bootstrap1.thenet.io"

# Set permissions
sudo chown -R thenet:thenet /opt/thenet/certs
sudo chmod 600 /opt/thenet/certs/server.key
```

### 6. Start and Enable Service

```bash
# Start service
sudo systemctl daemon-reload
sudo systemctl start thenet-bootstrap
sudo systemctl enable thenet-bootstrap

# Check status
sudo systemctl status thenet-bootstrap

# View logs
sudo journalctl -u thenet-bootstrap -f
```

## Docker Deployment

### Dockerfile

```dockerfile
FROM openjdk:17-slim

RUN useradd -r -s /bin/false thenet

WORKDIR /app

COPY thenet-bootstrap.jar /app/
COPY config.yaml /app/

RUN chown -R thenet:thenet /app

USER thenet

EXPOSE 8090

CMD ["java", "-Xms2G", "-Xmx4G", "-XX:+UseG1GC", \
     "-Dconfig.file=/app/config.yaml", \
     "-jar", "/app/thenet-bootstrap.jar"]
```

### Docker Compose

```yaml
version: '3.8'

services:
  bootstrap:
    build: .
    container_name: thenet-bootstrap
    restart: always
    ports:
      - "8090:8090"
    volumes:
      - ./config.yaml:/app/config.yaml:ro
      - ./certs:/app/certs:ro
      - bootstrap-data:/app/data
    environment:
      - JVM_OPTS=-Xms2G -Xmx4G
    ulimits:
      nofile:
        soft: 65536
        hard: 65536
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8090/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  bootstrap-data:
```

## Monitoring

### Health Check Endpoint

```bash
# Check health
curl http://bootstrap1.thenet.io:8090/health

# Expected response
{
  "status": "healthy",
  "uptime": 3600,
  "connections": 1523,
  "peersServed": 45123,
  "version": "1.0.0"
}
```

### Metrics Endpoint

```bash
# Get metrics (Prometheus format)
curl http://bootstrap1.thenet.io:8090/metrics

# Key metrics to monitor:
# - thenet_bootstrap_connections_total
# - thenet_bootstrap_connections_active
# - thenet_bootstrap_peers_served_total
# - thenet_bootstrap_response_time_seconds
# - thenet_bootstrap_errors_total
```

### Monitoring Stack

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'thenet-bootstrap'
    static_configs:
      - targets:
        - 'bootstrap1.thenet.io:8090'
        - 'bootstrap2.thenet.io:8090'
        - 'bootstrap3.thenet.io:8090'
```

## High Availability Setup

### Load Balancing

Use DNS round-robin or a load balancer:

```
# DNS configuration
bootstrap.thenet.io.  300  IN  A  192.0.2.1
bootstrap.thenet.io.  300  IN  A  192.0.2.2
bootstrap.thenet.io.  300  IN  A  192.0.2.3

# SRV records for discovery
_bootstrap._tcp.thenet.io.  300  IN  SRV  10  50  8090  bootstrap1.thenet.io.
_bootstrap._tcp.thenet.io.  300  IN  SRV  10  50  8090  bootstrap2.thenet.io.
_bootstrap._tcp.thenet.io.  300  IN  SRV  10  50  8090  bootstrap3.thenet.io.
```

### Geographic Distribution

Deploy bootstrap nodes in multiple regions:

- **US East**: bootstrap1.thenet.io
- **US West**: bootstrap2.thenet.io
- **EU**: bootstrap3.thenet.io
- **Asia**: bootstrap4.thenet.io

## Maintenance

### Regular Tasks

1. **Monitor logs** for errors and anomalies
2. **Update software** monthly or as security patches are released
3. **Rotate logs** to prevent disk space issues
4. **Review metrics** for performance degradation
5. **Test failover** quarterly

### Backup Configuration

```bash
#!/bin/bash
# backup-bootstrap.sh

BACKUP_DIR="/backup/thenet"
DATE=$(date +%Y%m%d)

# Backup configuration
tar -czf "$BACKUP_DIR/config-$DATE.tar.gz" /opt/thenet/bootstrap/config.yaml

# Backup certificates
tar -czf "$BACKUP_DIR/certs-$DATE.tar.gz" /opt/thenet/certs/

# Keep only last 30 days
find "$BACKUP_DIR" -name "*.tar.gz" -mtime +30 -delete
```

### Troubleshooting

Common issues and solutions:

1. **High CPU usage**
   - Check connection count: `ss -s`
   - Review logs for attack patterns
   - Enable rate limiting

2. **Connection refused**
   - Check firewall rules
   - Verify service is running
   - Check port binding

3. **SSL errors**
   - Verify certificate validity
   - Check certificate permissions
   - Review TLS configuration

4. **Memory issues**
   - Adjust JVM heap size
   - Enable G1GC garbage collector
   - Monitor for memory leaks

## Security Considerations

1. **DDoS Protection**
   - Use cloud-based DDoS protection
   - Implement rate limiting
   - Configure connection limits

2. **Access Control**
   - Restrict SSH access
   - Use key-based authentication
   - Enable fail2ban

3. **Updates**
   - Subscribe to security advisories
   - Test updates in staging first
   - Have rollback plan ready

4. **Monitoring**
   - Set up intrusion detection
   - Monitor for unusual patterns
   - Alert on security events

## Performance Tuning

### JVM Optimization

```bash
# High-performance JVM options
JAVA_OPTS="-Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=16M \
  -XX:+ParallelRefProcEnabled \
  -XX:+PerfDisableSharedMem \
  -XX:+AlwaysPreTouch \
  -Djava.net.preferIPv4Stack=true"
```

### Network Optimization

```bash
# TCP tuning for high connections
echo "net.core.somaxconn = 65535" >> /etc/sysctl.conf
echo "net.ipv4.tcp_max_syn_backlog = 65535" >> /etc/sysctl.conf
echo "net.core.netdev_max_backlog = 65535" >> /etc/sysctl.conf
sysctl -p
```

## Integration with TheNet

### Code Example

```kotlin
// Using bootstrap nodes in your application
val bootstrapService = BootstrapService(
    configuration = IPv8Configuration(
        bootstrapPeers = listOf(
            BootstrapPeer("bootstrap1.thenet.io", 8090),
            BootstrapPeer("bootstrap2.thenet.io", 8090),
            BootstrapPeer("bootstrap3.thenet.io", 8090)
        )
    )
)

// Start service
bootstrapService.start()

// Connect to bootstrap nodes
val peers = bootstrapService.connectToBootstrapNodes(count = 3)

// Monitor events
bootstrapService.connectionEvents.collect { event ->
    when (event) {
        is BootstrapConnectionEvent.Connected -> {
            println("Connected to ${event.node.id}")
        }
        is BootstrapConnectionEvent.Failed -> {
            println("Failed to connect: ${event.reason}")
        }
    }
}
```

## Conclusion

Properly deployed and maintained bootstrap nodes are essential for TheNet's P2P network reliability. Follow this guide to ensure your bootstrap nodes provide stable, secure, and performant service to the network.