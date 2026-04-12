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
#   revert        Revert pom.xml changes (restore from backup)
#   help          Show this help
# ============================================================================

set -euo pipefail
cd "$(dirname "$0")/../.."

MVN="./mvnw"
[ -f "$MVN" ] || MVN="mvn"

RULES_ARG="-Dmaven.version.rules=file://\${project.basedir}/version-rules.xml"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

banner() { echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${GREEN}  $1${NC}"; echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

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

    echo -e "\n${YELLOW}⚠  pom.xml has been modified. Review changes with:${NC}"
    echo -e "   git diff pom.xml"
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
    if [ -f pom.xml.versionsBackup ]; then
      mv pom.xml.versionsBackup pom.xml
      echo -e "${GREEN}✓ pom.xml restored from backup.${NC}"
    else
      echo -e "${YELLOW}No backup file found (pom.xml.versionsBackup).${NC}"
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
    echo "  revert         Revert pom.xml from backup"
    echo "  help           Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 check                    # See what's outdated"
    echo "  $0 update-props             # Auto-bump property versions"
    echo "  $0 update && mvn verify     # Update all and verify build"
    ;;
esac
