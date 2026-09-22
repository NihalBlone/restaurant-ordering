#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";

const ref = process.argv[2];
if (!ref || !/^[0-9a-f]{40}$/.test(ref) || process.argv.length !== 3) {
  console.error("Usage: node scripts/pin-frontend.mjs <published-40-character-UI-commit-SHA>");
  process.exit(1);
}
const file = new URL("../deploy/frontend.ref", import.meta.url);
const previous = readFileSync(file, "utf8").trim();
writeFileSync(file, `${ref}\n`);
console.log(`UI release pin: ${previous} -> ${ref}`);
console.log("Review git diff, then commit and push this backend repository to release that UI version.");
