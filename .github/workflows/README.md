# GitHub Actions Workflows

This directory contains GitHub Actions workflows for TheNet project's CI/CD pipeline.

## 🔧 Workflows Overview

### `ci.yml` - Main CI/CD Pipeline
**Trigger**: Push to `main`/`develop`, Pull Requests
**Purpose**: Comprehensive build, test, and quality checks

**Jobs**:
- **🔍 Gradle Validation**: Validates Gradle wrapper
- **🔨 Build & Test**: Matrix build across Linux/Windows/macOS with platform-specific builds
- **📋 Code Quality**: Runs detekt and ktlint checks
- **🔒 Security Scan**: Trivy vulnerability scanning
- **📦 Dependency Check**: Generates dependency tree
- **📊 Publish Reports**: Aggregates and publishes test results
- **📢 Notifications**: Notifies on build failures

**Artifacts Produced**:
- Test reports for all platforms
- Build artifacts (APK, desktop distributions)
- Code quality reports (detekt, ktlint)
- Security scan results
- Dependency trees

### `branch-protection.yml` - Branch Protection Setup
**Trigger**: Manual workflow dispatch
**Purpose**: Configures branch protection rules for `main` and `develop`

**Features**:
- Required status checks
- Required pull request reviews
- Conversation resolution requirements
- Force push and deletion prevention

### `release.yml` - Release Management
**Trigger**: Version tags (`v*`), Manual dispatch
**Purpose**: Builds and publishes releases

**Jobs**:
- **🚀 Build Release**: Matrix build for all platforms with release configuration
- **📦 Create Release**: Creates GitHub release with changelog and artifacts

**Artifacts**:
- Signed Android APK and AAB
- Desktop distributions for all platforms
- Library JARs

### `notifications.yml` - Build Status Notifications
**Trigger**: CI workflow completion
**Purpose**: Provides detailed build status notifications

**Features**:
- Auto-comments on related GitHub issues
- Build success/failure notifications
- Comprehensive build summaries
- Integration with task tracking

### `project-automation.yml` - Project Management
**Trigger**: Issue/PR events
**Purpose**: Automates project board management

**Features**:
- Auto-adds issues to appropriate project boards
- Applies labels based on task IDs
- Closes issues when related PRs merge

### `milestone-tracking.yml` - Progress Reporting
**Trigger**: Weekly schedule (Mondays 9 AM UTC), Manual
**Purpose**: Generates development progress reports

**Features**:
- Weekly progress reports
- Milestone completion tracking  
- Blocked issue identification
- Recently completed task summaries

## 🚀 Usage Guide

### For Developers

1. **Regular Development**:
   - Push to feature branches triggers CI checks
   - Open PRs to `develop` for code review
   - All status checks must pass before merge

2. **Code Quality**:
   - Fix any detekt or ktlint issues before merging
   - Review security scan results
   - Ensure all tests pass across platforms

3. **Task Tracking**:
   - Include task IDs (TN-XXX) in commit messages
   - Issues will be auto-updated with build status
   - Failed builds add `build-failure` label

### For Maintainers

1. **Branch Protection Setup**:
   ```bash
   # Run the branch protection workflow
   gh workflow run branch-protection.yml --field enable_protection=true
   ```

2. **Creating Releases**:
   ```bash
   # Tag-based release
   git tag v1.0.0
   git push origin v1.0.0
   
   # Manual release
   gh workflow run release.yml --field version=v1.0.0
   ```

3. **Monitoring**:
   - Check weekly milestone reports
   - Review security scan results
   - Monitor build failure notifications

## 🔧 Configuration

### Required Secrets

For full functionality, configure these repository secrets:

- `ANDROID_KEYSTORE_PASSWORD`: Android app signing
- `ANDROID_KEY_PASSWORD`: Android key password
- `GITHUB_TOKEN`: Automatic (for most workflows)

### Branch Protection

The workflow configures protection for:
- **main**: Full protection, admin enforcement
- **develop**: Relaxed protection for development

### Status Checks

Required status checks for protected branches:
- 🔍 Gradle Validation
- 🔨 Build & Test (all platforms)
- 📋 Code Quality
- 🔒 Security Scan

## 📊 Monitoring & Reports

### Build Artifacts
- Test reports: 30-day retention
- Build artifacts: 7-day retention
- Release artifacts: Permanent (in releases)

### Notifications
- Build failures create issue comments
- Weekly progress reports auto-generated
- Security issues flagged immediately

### Quality Gates
- All tests must pass
- Code quality checks must pass
- Security scans must pass
- Dependency vulnerabilities flagged

## 🛠️ Maintenance

### Regular Tasks
1. Update action versions quarterly
2. Review and update security scanning rules
3. Clean up old workflow runs periodically
4. Monitor build cache effectiveness

### Troubleshooting
- Check workflow logs for build failures
- Review artifact retention policies
- Verify secret configurations
- Monitor runner capacity and costs

## 📚 Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Gradle Build Action](https://github.com/gradle/actions)
- [Android Actions](https://github.com/android-actions)
- [Trivy Security Scanner](https://github.com/aquasecurity/trivy-action)