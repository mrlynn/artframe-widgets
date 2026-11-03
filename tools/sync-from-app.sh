#!/usr/bin/env bash
# Copies the TV app's widget parser into checker/, so this repo checks widgets with exactly
# the code the TV runs, and the spec, schema and examples into spec/. Run it whenever they
# change in the app repo, then commit.
#
#   tools/sync-from-app.sh                 # reads ../tv
#   TV_REPO=/path/to/tv tools/sync-from-app.sh
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
tv="${TV_REPO:-$here/../tv}"
src="$tv/artframe/app/src/main/kotlin/com/mrlynn/artframe/widget"
out="$here/checker/src/main/kotlin/vendor"
[[ -f "$src/WidgetSpec.kt" ]] || { echo "No widget parser in $src. Set TV_REPO." >&2; exit 1; }
rm -rf "$out" && mkdir -p "$out"
for f in Json.kt Expr.kt Wmo.kt WidgetSpec.kt HostPolicy.kt; do cp "$src/$f" "$out/"; done
commit="$(git -C "$tv" rev-parse --short HEAD)"
dirty="$(git -C "$tv" status --porcelain -- artframe/app/src/main/kotlin/com/mrlynn/artframe/widget | head -1)"
printf 'Copied from the ArtFrame app repo at %s%s by tools/sync-from-app.sh. Edit the app, not these.\n' \
  "$commit" "${dirty:+ (with uncommitted changes)}" > "$out/FROM.txt"
rm -rf "$here/spec" && mkdir -p "$here/spec"
cp "$tv/docs/widgets/SPEC.md" "$tv/docs/widgets/widget.schema.json" "$here/spec/"
cp -R "$tv/docs/widgets/examples" "$here/spec/"
# The built-ins, so the spec's "copy one and change it" works outside the app repo.
cp -R "$tv/artframe/app/src/main/assets/widgets" "$here/spec/built-in"
sed -i.bak 's|(../../artframe/app/src/main/assets/widgets)|(built-in)|; s|`artframe/app/src/main/assets/widgets`|`built-in`|' "$here/spec/SPEC.md" && rm "$here/spec/SPEC.md.bak"
echo "Parser synced from $commit${dirty:+ (uncommitted changes)}"
