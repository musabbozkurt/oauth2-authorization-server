#!/usr/bin/env bash
# ============================================================================
# update-versions.sh — Detect & update Maven dependency/plugin/property versions
#
# Usage:
#   ./docs/scripts/update-versions.sh [command]
#
# Commands:
#   check         Show all available updates (parent + dependencies + plugins + properties)
#   deps          Show only dependency updates
#   plugins       Show only plugin updates
#   props         Show only property updates
#   parent        Show parent POM updates
#   update        Auto-update all (parent + properties + dependencies) to latest releases
#   update-parent Auto-update only the parent POM version
#   update-props  Auto-update only properties (safest, recommended)
#   update-deps   Auto-update dependencies to latest releases
#   java          Update java.version (pom.xml) and CI JDK pins to the latest Java GA release
#   revert        Revert pom.xml and CI workflow changes (restore from backup)
#   help          Show this help
# ============================================================================

set -euo pipefail
cd "$(dirname "$0")/../.."

MVN="./mvnw"
[ -f "$MVN" ] || MVN="mvn"

# Files touched by the Java upgrade; each gets a <file>.versionsBackup so `revert` can restore it.
JAVA_FILES=(pom.xml .github/workflows/*.yml Dockerfile DockerfileForAOTCache README.md docs/PROJECT_STRUCTURE.md)

RULES_ARG="-Dmaven.version.rules=file://\${project.basedir}/version-rules.xml"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

banner() { echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${GREEN}  $1${NC}"; echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

# Returns success when <repo>:<tag> is published on Docker Hub (repo like library/eclipse-temurin).
docker_tag_exists() { curl -fsS -o /dev/null "https://hub.docker.com/v2/repositories/$1/tags/$2/"; }

# Upgrades java.version (pom.xml), CI JDK pins, Java mentions in docs, and Dockerfile JDK images to the
# latest Java GA feature release reported by the Adoptium API. Dockerfiles are only changed when every
# new image tag is published, so a build never references a missing tag.
update_java() {
  local latest current maven_tag_now maven_tag_new f
  latest=$(curl -fsSL https://api.adoptium.net/v3/info/available_releases \
    | grep -o '"most_recent_feature_release": *[0-9]*' | grep -o '[0-9]*$')
  current=$(sed -n 's:.*<java.version>\(.*\)</java.version>.*:\1:p' pom.xml | head -1)
  echo "Current java.version: ${current:-unknown}, latest Java GA: ${latest}"

  for f in "${JAVA_FILES[@]}"; do cp "$f" "$f.versionsBackup"; done

  if [ "$current" != "$latest" ]; then
    $MVN versions:set-property -Dproperty=java.version -DnewVersion="$latest" -DgenerateBackupPoms=false | cat
  fi

  sed -i.bak -E "s/java-version: '[0-9]+'/java-version: '${latest}'/; s/Set up JDK [0-9]+/Set up JDK ${latest}/" .github/workflows/*.yml
  sed -i.bak -E "s/<!-- Java [0-9]+ -->/<!-- Java ${latest} -->/" pom.xml
  # Skip lines with URLs so third-party titles (e.g. "Java 21" in a video title) are not rewritten.
  sed -i.bak -E "/https?:\/\//! s/Java [0-9]+([^0-9]|$)/Java ${latest}\1/g; /https?:\/\//! s/-v [0-9]+\)/-v ${latest})/g" README.md docs/PROJECT_STRUCTURE.md
  rm -f .github/workflows/*.yml.bak pom.xml.bak README.md.bak docs/PROJECT_STRUCTURE.md.bak

  maven_tag_now=$(sed -n 's/^FROM maven:\([^ ]*\).*/\1/p' Dockerfile | head -1)
  maven_tag_new=$(echo "$maven_tag_now" | sed -E "s/eclipse-temurin-[0-9]+-/eclipse-temurin-${latest}-/")
  if docker_tag_exists library/maven "$maven_tag_new" \
    && docker_tag_exists library/eclipse-temurin "${latest}-jdk-resolute" \
    && docker_tag_exists bellsoft/liberica-runtime-container "jdk-${latest}-stream-musl" \
    && docker_tag_exists bellsoft/liberica-runtime-container "jdk-${latest}-cds-slim-musl"; then
    sed -i.bak -E "s/eclipse-temurin-[0-9]+-resolute/eclipse-temurin-${latest}-resolute/; s/eclipse-temurin:[0-9]+-jdk-resolute/eclipse-temurin:${latest}-jdk-resolute/; s#/opt/java/jdk[0-9]+#/opt/java/jdk${latest}#; s/liberica-runtime-container:jdk-[0-9]+-/liberica-runtime-container:jdk-${latest}-/; s/Java [0-9]+([^0-9]|$)/Java ${latest}\1/g" Dockerfile DockerfileForAOTCache
    rm -f Dockerfile.bak DockerfileForAOTCache.bak
    echo -e "${GREEN}✓ Dockerfiles updated to JDK ${latest}.${NC}"
  else
    echo -e "${YELLOW}⚠  JDK ${latest} images not all published on Docker Hub yet; Dockerfiles left unchanged.${NC}"
  fi
}


cmd="${1:-help}"

case "$cmd" in

  check)
    banner "Checking ALL available updates (including parent POM)..."
    $MVN versions:display-parent-updates versions:display-dependency-updates versions:display-plugin-updates versions:display-property-updates | cat
    ;;

  deps)
    banner "Checking dependency updates..."
    $MVN versions:display-dependency-updates | cat
    ;;

  plugins)
    banner "Checking plugin updates..."
    $MVN versions:display-plugin-updates | cat
    ;;

  props)
    banner "Checking property updates..."
    $MVN versions:display-property-updates | cat
    ;;

  parent)
    banner "Checking parent POM updates..."
    $MVN versions:display-parent-updates | cat
    ;;

  update)
    banner "Creating backup of pom.xml..."
    cp pom.xml pom.xml.versionsBackup

    banner "Updating parent POM to latest version..."
    $MVN versions:update-parent -DgenerateBackupPoms=false | cat

    banner "Updating properties to latest versions..."
    $MVN versions:update-properties -DgenerateBackupPoms=false | cat

    banner "Updating dependencies to latest releases..."
    $MVN versions:use-latest-releases -DgenerateBackupPoms=false | cat

    echo -e "\n${YELLOW}⚠  pom.xml has been modified. Review changes with:${NC}"
    echo -e "   git diff pom.xml"
    echo -e "   # or revert: ./docs/scripts/update-versions.sh revert"
    ;;

  update-props)
    banner "Creating backup of pom.xml..."
    cp pom.xml pom.xml.versionsBackup

    banner "Updating properties to latest versions..."
    $MVN versions:update-properties -DgenerateBackupPoms=false | cat

    echo -e "\n${YELLOW}⚠  pom.xml has been modified. Review changes with:${NC}"
    echo -e "   git diff pom.xml"
    ;;

  update-deps)
    banner "Creating backup of pom.xml..."
    cp pom.xml pom.xml.versionsBackup

    banner "Updating dependencies to latest releases..."
    $MVN versions:use-latest-releases -DgenerateBackupPoms=false | cat

    banner "Updating Java to latest GA release..."
    update_java

    echo -e "\n${YELLOW}⚠  pom.xml has been modified. Review changes with:${NC}"
    echo -e "   git diff pom.xml"
    ;;

  java)
    banner "Creating backup of pom.xml..."
    cp pom.xml pom.xml.versionsBackup

    banner "Updating Java to latest GA release..."
    update_java

    echo -e "\n${YELLOW}⚠  pom.xml, CI workflows and Docker/docs Java references have been modified. Review changes with:${NC}"
    echo -e "   git diff"
    ;;

  update-parent)
    banner "Creating backup of pom.xml..."
    cp pom.xml pom.xml.versionsBackup

    banner "Updating parent POM (spring-boot-starter-parent) to latest version..."
    $MVN versions:update-parent -DgenerateBackupPoms=false | cat

    echo -e "\n${YELLOW}⚠  pom.xml has been modified. Review changes with:${NC}"
    echo -e "   git diff pom.xml"
    ;;

  revert)
    restored=0
    for f in "${JAVA_FILES[@]}"; do
      if [ -f "$f.versionsBackup" ]; then
        mv "$f.versionsBackup" "$f"
        echo -e "${GREEN}✓ $f restored from backup.${NC}"
        restored=1
      fi
    done
    if [ "$restored" = 0 ]; then
      echo -e "${YELLOW}No backup files found (*.versionsBackup).${NC}"
      exit 1
    fi
    ;;

  help|*)
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  check          Show all available updates (parent + dependencies + plugins + properties)"
    echo "  deps           Show only dependency updates"
    echo "  plugins        Show only plugin updates"
    echo "  props          Show only property updates"
    echo "  parent         Show parent POM (spring-boot-starter-parent) updates"
    echo "  update         Auto-update ALL (parent + properties + dependencies) to latest releases"
    echo "  update-parent  Auto-update only the parent POM version"
    echo "  update-props   Auto-update only <properties> versions (safest, recommended)"
    echo "  update-deps    Auto-update dependencies to latest releases"
    echo "  java           Update java.version and CI JDK pins to latest Java GA release"
    echo "  revert         Revert pom.xml and CI workflows from backup"
    echo "  help           Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 check                    # See what's outdated"
    echo "  $0 update-props             # Auto-bump property versions"
    echo "  $0 update && mvn verify     # Update all and verify build"
    ;;
esac
