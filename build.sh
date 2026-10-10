#!/bin/bash
# ============================================================
#  InvestPlugin Build Script
#  Requirements: Java 21+, Maven 3.6+
#  Usage: chmod +x build.sh && ./build.sh
# ============================================================

echo "=============================="
echo "  InvestPlugin Build Script   "
echo "=============================="

# Check Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java not found. Please install Java 21."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
echo "[INFO] Java version detected: $JAVA_VERSION"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "[ERROR] Maven not found. Please install Maven 3.6+."
    echo "  Ubuntu/Debian: sudo apt install maven"
    echo "  macOS: brew install maven"
    echo "  Windows: https://maven.apache.org/download.cgi"
    exit 1
fi

echo "[INFO] Building InvestPlugin..."
mvn clean package -q

if [ $? -eq 0 ]; then
    echo ""
    echo "[SUCCESS] Build complete!"
    echo "[INFO] Plugin jar located at: target/InvestPlugin-2.0.0.jar"
    echo ""
    echo "Installation:"
    echo "  1. Copy target/InvestPlugin-2.0.0.jar to your server's /plugins folder"
    echo "  2. Install Vault plugin: https://www.spigotmc.org/resources/vault.34315/"
    echo "  3. Install an economy plugin (e.g. EssentialsX)"
    echo "  4. Restart your server"
    echo ""
    echo "Configuration: plugins/InvestPlugin/config.yml"
else
    echo "[ERROR] Build failed. Check the output above for errors."
    exit 1
fi
