# Claude Code Setup Script for Windows PowerShell
# Run this script as Administrator for best results

param(
    [switch]$SkipChocolatey,
    [switch]$SkipAndroidStudio,
    [switch]$QuietMode
)

# Set execution policy and error handling
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-ColorOutput {
    param([string]$Message, [string]$Color = "White")
    if (-not $QuietMode) {
        Write-Host $Message -ForegroundColor $Color
    }
}

function Test-AdminRights {
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentUser)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Install-Chocolatey {
    if ($SkipChocolatey) {
        Write-ColorOutput "Skipping Chocolatey installation..." "Yellow"
        return
    }
    
    Write-ColorOutput "Installing Chocolatey package manager..." "Cyan"
    
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        Write-ColorOutput "Chocolatey already installed!" "Green"
        return
    }
    
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    
    # Refresh PATH
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH", "User")
    
    Write-ColorOutput "Chocolatey installed successfully!" "Green"
}

function Install-RequiredSoftware {
    Write-ColorOutput "Installing required software..." "Cyan"
    
    # Essential tools
    $packages = @(
        "git",
        "nodejs",
        "python",
        "curl",
        "wget"
    )
    
    # Java Development Kit
    Write-ColorOutput "Installing OpenJDK 17..." "Cyan"
    choco install openjdk17 -y
    
    # Install other packages
    foreach ($package in $packages) {
        Write-ColorOutput "Installing $package..." "Cyan"
        choco install $package -y
    }
    
    # Docker Desktop (optional but recommended)
    $installDocker = Read-Host "Install Docker Desktop? (y/N)"
    if ($installDocker -eq "y" -or $installDocker -eq "Y") {
        Write-ColorOutput "Installing Docker Desktop..." "Cyan"
        choco install docker-desktop -y
    }
    
    Write-ColorOutput "Software installation complete!" "Green"
}

function Install-AndroidStudio {
    if ($SkipAndroidStudio) {
        Write-ColorOutput "Skipping Android Studio installation..." "Yellow"
        return
    }
    
    $installAndroid = Read-Host "Install Android Studio for Android development? (y/N)"
    if ($installAndroid -eq "y" -or $installAndroid -eq "Y") {
        Write-ColorOutput "Installing Android Studio..." "Cyan"
        choco install androidstudio -y
        Write-ColorOutput "Android Studio installed! Please set up Android SDK manually." "Green"
    }
}

function Install-ClaudeCode {
    Write-ColorOutput "Installing Claude Code CLI..." "Cyan"
    
    # Check if npm is available
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Write-ColorOutput "Error: npm not found. Please restart PowerShell or refresh PATH." "Red"
        return
    }
    
    # Install Claude Code globally
    npm install -g @anthropic-ai/claude-code
    
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "Claude Code installed successfully!" "Green"
    } else {
        Write-ColorOutput "Error installing Claude Code via npm." "Red"
        Write-ColorOutput "Please install manually using: npm install -g @anthropic-ai/claude-code" "Yellow"
    }
}

function Set-EnvironmentVariables {
    Write-ColorOutput "Setting up environment variables..." "Cyan"
    
    # Set JAVA_HOME
    $javaPath = Get-ChildItem "C:\Program Files\OpenJDK" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($javaPath) {
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaPath.FullName, "User")
        Write-ColorOutput "JAVA_HOME set to: $($javaPath.FullName)" "Green"
    }
    
    # Set Android environment (if Android Studio was installed)
    $androidSdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $androidSdkPath) {
        [Environment]::SetEnvironmentVariable("ANDROID_HOME", $androidSdkPath, "User")
        [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $androidSdkPath, "User")
        Write-ColorOutput "Android SDK environment variables set!" "Green"
    }
    
    # PowerShell profile for UTF-8 support
    $profileContent = @"
# Claude Code PowerShell Profile Setup
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

# Set PSReadLine options for better experience
if (Get-Module -ListAvailable PSReadLine) {
    Set-PSReadLineOption -PredictionSource History
    Set-PSReadLineOption -HistorySearchCursorMovesToEnd
}

# Aliases for common commands
function Start-Gradle { .\gradlew.bat @args }
Set-Alias -Name gradle -Value Start-Gradle
Set-Alias -Name ll -Value Get-ChildItem

# Claude Code helper function
function Start-ClaudeSession {
    param([string]`$Message = "Help me with development")
    claude `$Message
}
Set-Alias -Name cc -Value Start-ClaudeSession

Write-Host "Claude Code PowerShell environment loaded!" -ForegroundColor Green
"@
    
    # Create PowerShell profile if it doesn't exist
    if (-not (Test-Path $PROFILE)) {
        New-Item -ItemType File -Force -Path $PROFILE
    }
    
    # Add profile content (only if not already present)
    $currentContent = Get-Content $PROFILE -ErrorAction SilentlyContinue
    if (-not ($currentContent -like "*Claude Code PowerShell Profile Setup*")) {
        Add-Content -Path $PROFILE -Value $profileContent
        Write-ColorOutput "PowerShell profile updated with Claude Code optimizations!" "Green"
    } else {
        Write-ColorOutput "PowerShell profile already configured!" "Yellow"
    }
}

function Test-Installation {
    Write-ColorOutput "Testing installation..." "Cyan"
    
    # Test Java
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $javaVersion = java -version 2>&1 | Select-String "openjdk version" | Select-Object -First 1
        Write-ColorOutput "✓ Java: $javaVersion" "Green"
    } else {
        Write-ColorOutput "✗ Java not found in PATH" "Red"
    }
    
    # Test Git
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $gitVersion = git --version
        Write-ColorOutput "✓ $gitVersion" "Green"
    } else {
        Write-ColorOutput "✗ Git not found in PATH" "Red"
    }
    
    # Test Node.js
    if (Get-Command node -ErrorAction SilentlyContinue) {
        $nodeVersion = node --version
        Write-ColorOutput "✓ Node.js: $nodeVersion" "Green"
    } else {
        Write-ColorOutput "✗ Node.js not found in PATH" "Red"
    }
    
    # Test Claude Code
    if (Get-Command claude -ErrorAction SilentlyContinue) {
        Write-ColorOutput "✓ Claude Code CLI installed" "Green"
    } else {
        Write-ColorOutput "✗ Claude Code CLI not found in PATH" "Red"
        Write-ColorOutput "  Try restarting PowerShell or running: npm install -g @anthropic-ai/claude-code" "Yellow"
    }
}

function Show-NextSteps {
    Write-ColorOutput "" "White"
    Write-ColorOutput "🎉 Setup Complete! Next Steps:" "Green"
    Write-ColorOutput "1. Restart PowerShell to refresh PATH and load profile" "Cyan"
    Write-ColorOutput "2. Authenticate Claude Code: claude auth login" "Cyan"
    Write-ColorOutput "3. Test with: claude 'Hello, can you help me?'" "Cyan"
    Write-ColorOutput "4. For TheNet development:" "Cyan"
    Write-ColorOutput "   - cd to your project directory" "Cyan"
    Write-ColorOutput "   - Run: .\gradlew.bat build" "Cyan"
    Write-ColorOutput "   - Run: .\gradlew.bat :desktop:run" "Cyan"
    Write-ColorOutput "" "White"
    Write-ColorOutput "Useful aliases added to your PowerShell profile:" "Yellow"
    Write-ColorOutput "  cc 'your message'  - Quick Claude Code command" "Yellow"
    Write-ColorOutput "  ll                 - List files (like ls -la)" "Yellow"
    Write-ColorOutput "  gradle             - Alias for .\gradlew.bat" "Yellow"
    Write-ColorOutput "" "White"
    Write-ColorOutput "Documentation: https://docs.anthropic.com/en/docs/claude-code" "Cyan"
}

# Main execution
function Main {
    Write-ColorOutput "🚀 Claude Code Setup for Windows PowerShell" "Green"
    Write-ColorOutput "===========================================" "Green"
    
    # Check admin rights
    if (-not (Test-AdminRights)) {
        Write-ColorOutput "Warning: Running without administrator privileges." "Yellow"
        Write-ColorOutput "Some installations may fail. Consider running as administrator." "Yellow"
        $continue = Read-Host "Continue anyway? (y/N)"
        if ($continue -ne "y" -and $continue -ne "Y") {
            exit 1
        }
    }
    
    try {
        Install-Chocolatey
        Install-RequiredSoftware
        Install-AndroidStudio
        Install-ClaudeCode
        Set-EnvironmentVariables
        Test-Installation
        Show-NextSteps
        
        Write-ColorOutput "" "White"
        Write-ColorOutput "✅ Setup completed successfully!" "Green"
        Write-ColorOutput "Please restart PowerShell to use all new tools." "Yellow"
        
    } catch {
        Write-ColorOutput "❌ Error during setup: $($_.Exception.Message)" "Red"
        Write-ColorOutput "Please check the error and try again." "Red"
        exit 1
    }
}

# Run the main function
Main