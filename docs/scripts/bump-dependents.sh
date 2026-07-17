#!/usr/bin/env bash
# =============================================================================
# bump-dependents.sh
#
# Automatically detects which module versions changed in pom.xml (compared to
# the base branch), determines the bump type (major/minor/patch), and applies
# the same bump type to all dependent modules.
#
# Usage:
#   ./bump-dependents.sh [base-branch]
#
# Examples:
#   ./bump-dependents.sh              # compares against origin/main
#   ./bump-dependents.sh origin/dev   # compares against origin/dev
#
# What it does:
#   1. Diffs pom.xml against the base branch to find changed version properties.
#   2. Determines the bump type (major, minor, or patch) for each changed module.
#   3. Finds all sibling modules that depend on each changed module.
#   4. Applies the same bump type to each dependent module's version.
#   5. Prints a summary of all changes.
#
# Integrate into CI:
#   Run this script after version property changes, then commit the result.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PARENT_POM="$SCRIPT_DIR/pom.xml"
BASE_BRANCH="${1:-origin/main}"

# --- Helpers ---

# Compare two versions and return the bump type: major, minor, or patch
detect_bump_type() {
    local old="$1" new="$2"

    # Strip -SNAPSHOT
    old="${old%-SNAPSHOT}"
    new="${new%-SNAPSHOT}"

    local OLD_MAJOR OLD_MINOR OLD_PATCH NEW_MAJOR NEW_MINOR NEW_PATCH
    IFS='.' read -r OLD_MAJOR OLD_MINOR OLD_PATCH <<< "$old"
    IFS='.' read -r NEW_MAJOR NEW_MINOR NEW_PATCH <<< "$new"

    if [ "$NEW_MAJOR" -gt "$OLD_MAJOR" ]; then
        echo "major"
    elif [ "$NEW_MINOR" -gt "$OLD_MINOR" ]; then
        echo "minor"
    else
        echo "patch"
    fi
}

# Bump a version string by a given type
bump_version() {
    local version="$1" bump_type="$2"

    local is_snapshot=false
    local base="$version"
    if [[ "$version" == *-SNAPSHOT ]]; then
        is_snapshot=true
        base="${version%-SNAPSHOT}"
    fi

    local major minor patch
    IFS='.' read -r major minor patch <<< "$base"

    case "$bump_type" in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
    esac

    local new_base="${major}.${minor}.${patch}"
    if [ "$is_snapshot" = true ]; then
        echo "${new_base}-SNAPSHOT"
    else
        echo "$new_base"
    fi
}

# Check if a value exists in a space-separated list
contains() {
    local list="$1" item="$2"
    [[ " $list " == *" $item "* ]]
}

# BFS over sibling modules to collect all transitive dependents of root.
collect_transitive_dependents() {
    local root="$1"
    local queue="$root"
    local visited="$root"
    local result=""

    while [ -n "$queue" ]; do
        local current="${queue%% *}"
        if [ "$queue" = "$current" ]; then
            queue=""
        else
            queue="${queue#* }"
        fi

        for module_dir in "$SCRIPT_DIR"/common-*/; do
            local module_name module_pom
            module_name=$(basename "$module_dir")
            module_pom="$module_dir/pom.xml"

            [ "$module_name" = "$current" ] && continue
            [ ! -f "$module_pom" ] && continue

            if grep -q "<artifactId>${current}</artifactId>" "$module_pom"; then
                if ! contains "$visited" "$module_name"; then
                    visited="$visited $module_name"
                    queue="$queue $module_name"
                    result="$result $module_name"
                fi
            fi
        done
    done

    echo "$result"
}

echo "🔍 Detecting version changes in pom.xml compared to '$BASE_BRANCH'..."
echo ""

# Get the old pom.xml from the base branch
RELATIVE_POM=$(git -C "$SCRIPT_DIR" ls-files --full-name pom.xml 2>/dev/null || echo "pom.xml")
OLD_POM=$(git -C "$SCRIPT_DIR" show "$BASE_BRANCH:$RELATIVE_POM" 2>/dev/null || echo "")

if [ -z "$OLD_POM" ]; then
    echo "❌ Could not read pom.xml from '$BASE_BRANCH'. Make sure the branch exists and pom.xml is tracked."
    exit 1
fi

# Capture modules whose version properties changed vs base.
CHANGED_MODULE_NAMES=()
CHANGED_MODULE_BUMPS=()
CHANGED_MODULE_IS_SNAPSHOT=()
CHANGED_LIST=""

while IFS= read -r prop_name; do
    # Extract old and new values
    OLD_VAL=$(echo "$OLD_POM" | sed -n "s|.*<${prop_name}>\([^<]*\)</${prop_name}>.*|\1|p")
    NEW_VAL=$(sed -n "s|.*<${prop_name}>\([^<]*\)</${prop_name}>.*|\1|p" "$PARENT_POM")

    if [ -n "$OLD_VAL" ] && [ -n "$NEW_VAL" ] && [ "$OLD_VAL" != "$NEW_VAL" ]; then
        MODULE_NAME="${prop_name%.version}"
        BUMP_TYPE=$(detect_bump_type "$OLD_VAL" "$NEW_VAL")
        [[ "$NEW_VAL" == *-SNAPSHOT ]] && IS_SNAP=true || IS_SNAP=false

        if ! contains "$CHANGED_LIST" "$MODULE_NAME"; then
            CHANGED_MODULE_NAMES+=("$MODULE_NAME")
            CHANGED_MODULE_BUMPS+=("$BUMP_TYPE")
            CHANGED_MODULE_IS_SNAPSHOT+=("$IS_SNAP")
            CHANGED_LIST="$CHANGED_LIST $MODULE_NAME"
            echo "   📦 $MODULE_NAME: $OLD_VAL → $NEW_VAL ($BUMP_TYPE bump)"
        fi
    fi
done < <(sed -n 's|.*<\(common-[a-z]*\.version\)>.*|\1|p' "$PARENT_POM")

if [ ${#CHANGED_MODULE_NAMES[@]} -eq 0 ]; then
    echo "✅ No module version changes detected. Nothing to do."
    exit 0
fi

echo ""
echo "🔗 Finding and bumping dependent modules..."
echo ""

BUMPED=()
PROCESSED=""

for i in "${!CHANGED_MODULE_NAMES[@]}"; do
    changed_module="${CHANGED_MODULE_NAMES[$i]}"
    bump_type="${CHANGED_MODULE_BUMPS[$i]}"
    trigger_is_snapshot="${CHANGED_MODULE_IS_SNAPSHOT[$i]}"

    for dependent in $(collect_transitive_dependents "$changed_module"); do
        # Skip modules already changed manually or already bumped in this run.
        contains "$CHANGED_LIST" "$dependent" && continue
        contains "$PROCESSED" "$dependent" && continue

        PROP_NAME="${dependent}.version"
        CURRENT_VERSION=$(sed -n "s|.*<${PROP_NAME}>\([^<]*\)</${PROP_NAME}>.*|\1|p" "$PARENT_POM")

        if [ -z "$CURRENT_VERSION" ]; then
            echo "   ⚠️  Could not find <${PROP_NAME}> in pom.xml, skipping."
            continue
        fi

        NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$bump_type")

        # If the triggering module is a SNAPSHOT, dependents must also be SNAPSHOT.
        if [ "$trigger_is_snapshot" = true ] && [[ "$NEW_VERSION" != *-SNAPSHOT ]]; then
            NEW_VERSION="${NEW_VERSION}-SNAPSHOT"
        fi

        # Portable in-place sed for macOS and Linux.
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|<${PROP_NAME}>${CURRENT_VERSION}</${PROP_NAME}>|<${PROP_NAME}>${NEW_VERSION}</${PROP_NAME}>|" "$PARENT_POM"
        else
            sed -i "s|<${PROP_NAME}>${CURRENT_VERSION}</${PROP_NAME}>|<${PROP_NAME}>${NEW_VERSION}</${PROP_NAME}>|" "$PARENT_POM"
        fi

        BUMPED+=("$dependent: $CURRENT_VERSION → $NEW_VERSION ($bump_type, via $changed_module)")
        PROCESSED="$PROCESSED $dependent"
    done
done

# Final pass: ensure all dependents of SNAPSHOT triggers are also SNAPSHOT.
SNAPSHOT_ADDED=()
for i in "${!CHANGED_MODULE_NAMES[@]}"; do
    changed_module="${CHANGED_MODULE_NAMES[$i]}"
    is_snapshot="${CHANGED_MODULE_IS_SNAPSHOT[$i]}"

    if [ "$is_snapshot" = true ]; then
        for dependent in $(collect_transitive_dependents "$changed_module"); do
            PROP_NAME="${dependent}.version"
            CURRENT_VERSION=$(sed -n "s|.*<${PROP_NAME}>\([^<]*\)</${PROP_NAME}>.*|\1|p" "$PARENT_POM")

            if [ -n "$CURRENT_VERSION" ] && [[ "$CURRENT_VERSION" != *-SNAPSHOT ]]; then
                NEW_VERSION="${CURRENT_VERSION}-SNAPSHOT"

                # Portable in-place sed for macOS and Linux.
                if [[ "$OSTYPE" == "darwin"* ]]; then
                    sed -i '' "s|<${PROP_NAME}>${CURRENT_VERSION}</${PROP_NAME}>|<${PROP_NAME}>${NEW_VERSION}</${PROP_NAME}>|" "$PARENT_POM"
                else
                    sed -i "s|<${PROP_NAME}>${CURRENT_VERSION}</${PROP_NAME}>|<${PROP_NAME}>${NEW_VERSION}</${PROP_NAME}>|" "$PARENT_POM"
                fi

                SNAPSHOT_ADDED+=("$dependent: $CURRENT_VERSION → $NEW_VERSION (SNAPSHOT propagation from $changed_module)")
            fi
        done
    fi
done

if [ ${#BUMPED[@]} -eq 0 ] && [ ${#SNAPSHOT_ADDED[@]} -eq 0 ]; then
    echo "✅ No dependent modules need bumping or SNAPSHOT adjustment."
    exit 0
fi

if [ ${#BUMPED[@]} -gt 0 ]; then
    echo "✅ Dependent version bumps applied:"
    for b in "${BUMPED[@]}"; do
        echo "   $b"
    done
    echo ""
fi

if [ ${#SNAPSHOT_ADDED[@]} -gt 0 ]; then
    echo "✅ SNAPSHOT suffix propagated:"
    for s in "${SNAPSHOT_ADDED[@]}"; do
        echo "   $s"
    done
    echo ""
fi

echo "📝 Changes made in: $PARENT_POM"
echo "   Remember to commit these changes."
