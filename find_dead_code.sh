#!/bin/bash
# Find internal fun/val/var declarations with zero callers anywhere else in the codebase.
cd /home/ubuntu/alextool/app/src/main/java || exit 1
while IFS= read -r decl; do
  # decl format: Name(params...)
  name="${decl%%(*}"
  name="${name%:}"
  # count references to the name in other files (skip the declaring file)
  count=$(grep -rlw "$name" --include="*.kt" . | wc -l)
  # also check XML layouts (e.g. onClick= function references are in XML-free compose, but be safe)
  xmlcount=$(grep -rlw "$name" --include="*.xml" res/ 2>/dev/null | wc -l)
  total=$((count + xmlcount))
  if [ "$total" -le 0 ]; then
    echo "DEAD: $decl"
  fi
done < /tmp/internal_decls.txt
