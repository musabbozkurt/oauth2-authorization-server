#!/usr/bin/env bash
# =============================================================================
# validate-version-cascade.sh
#
# CI gate: validates that when a module version was bumped, all dependent
# modules also had their versions bumped compared to a base branch.
# Additionally, if a bumped module is a SNAPSHOT, all its dependents must
# also carry the -SNAPSHOT suffix.
#
# Usage:
#   ./validate-version-cascade.sh [base-branch]
#
# Example:
#   ./validate-version-cascade.sh origin/main
#
# Exit codes:
#   0 — All dependent modules are properly bumped (and SNAPSHOT-consistent).
#   1 — Some dependent modules need a version bump or SNAPSHOT suffix.
#
# Integrate into CI pipeline as a validation step before merge.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PARENT_POM="$SCRIPT_DIR/pom.xml"
BASE_BRANCH="${1:-origin/main}"

# Word-based membership check for space-separated lists.
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

echo "🔍 Comparing version properties against '$BASE_BRANCH'..."
echo ""

# Collect changed common-*.version properties from git diff.
CHANGED_MODULES=()
CHANGED_LIST=""
SNAPSHOT_TRIGGERS=""   # space-separated list of modules whose new version is -SNAPSHOT
while IFS= read -r line; do
    if [[ "$line" =~ \+.*\<(common-[a-z]+)\.version\>([^<]*)\< ]]; then
        MODULE="${BASH_REMATCH[1]}"
        NEW_VAL="${BASH_REMATCH[2]}"
        if ! contains "$CHANGED_LIST" "$MODULE"; then
            CHANGED_MODULES+=("$MODULE")
            CHANGED_LIST="$CHANGED_LIST $MODULE"
            [[ "$NEW_VAL" == *-SNAPSHOT ]] && SNAPSHOT_TRIGGERS="$SNAPSHOT_TRIGGERS $MODULE"
        fi
    fi
done < <(git diff "$BASE_BRANCH" -- "$PARENT_POM" 2>/dev/null || echo "")

if [ ${#CHANGED_MODULES[@]} -eq 0 ]; then
    echo "✅ No module versions changed. Nothing to validate."
    exit 0
fi

echo "📦 Modules with version bumps:"
for mod in "${CHANGED_MODULES[@]}"; do
    echo "   - $mod"
done
echo ""

# Fail if any dependent is missing from the changed module list.
MISSING_BUMPS=()
MISSING_KEYS=""

for changed in "${CHANGED_MODULES[@]}"; do
    for dependent in $(collect_transitive_dependents "$changed"); do
        if ! contains "$CHANGED_LIST" "$dependent"; then
            key="$dependent|$changed"
            if ! contains "$MISSING_KEYS" "$key"; then
                MISSING_BUMPS+=("$dependent (depends transitively on $changed)")
                MISSING_KEYS="$MISSING_KEYS $key"
            fi
        fi
    done
done

if [ ${#MISSING_BUMPS[@]} -eq 0 ]; then
    echo "✅ All dependent modules have been properly bumped."
else
    echo "❌ The following modules depend on a changed module but were NOT bumped:"
    for missing in "${MISSING_BUMPS[@]}"; do
        echo "   - $missing"
    done
    echo ""
    echo "💡 Fix: run './scripts/bump-dependents.sh <base-branch>' to auto-cascade bumps, or bump them manually."
    exit 1
fi

# Check that dependents of SNAPSHOT triggers also carry the -SNAPSHOT suffix.
MISSING_SNAPSHOT=()
MISSING_SNAP_KEYS=""

for trigger in $SNAPSHOT_TRIGGERS; do
    for dependent in $(collect_transitive_dependents "$trigger"); do
        PROP_NAME="${dependent}.version"
        CURRENT_VERSION=$(sed -n "s|.*<${PROP_NAME}>\([^<]*\)</${PROP_NAME}>.*|\1|p" "$PARENT_POM")
        if [[ "$CURRENT_VERSION" != *-SNAPSHOT ]]; then
            key="$dependent|$trigger"
            if ! contains "$MISSING_SNAP_KEYS" "$key"; then
                MISSING_SNAPSHOT+=("$dependent ($CURRENT_VERSION) must be -SNAPSHOT because $trigger is a SNAPSHOT")
                MISSING_SNAP_KEYS="$MISSING_SNAP_KEYS $key"
            fi
        fi
    done
done

if [ ${#MISSING_SNAPSHOT[@]} -eq 0 ]; then
    echo "✅ All dependent modules have the correct -SNAPSHOT suffix."
    exit 0
else
    echo "❌ The following dependent modules are missing the -SNAPSHOT suffix:"
    for m in "${MISSING_SNAPSHOT[@]}"; do
        echo "   - $m"
    done
    echo ""
    echo "💡 Fix: run './scripts/bump-dependents.sh <base-branch>' to auto-cascade bumps, or add -SNAPSHOT manually."
    exit 1
fi
