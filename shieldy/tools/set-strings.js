// usage: node tools/set-strings.js <res dir> <translations.json>
// JSON: { "en": { "key": "value", ... }, "ru": { ... }, ... } with plain text values.
// Replaces <string name="key"> in each values[-lang]/strings.xml, or appends it before </resources>.
const fs = require("fs"), path = require("path");
const [res, file] = process.argv.slice(2);
const all = JSON.parse(fs.readFileSync(file, "utf8"));
// Android wants apostrophes escaped; already-escaped ones are left alone.
const esc = s => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/(?<!\\)'/g, "\\'");
for (const [lang, map] of Object.entries(all)) {
  const f = path.join(res, lang === "en" ? "values" : "values-" + lang, "strings.xml");
  let xml = fs.readFileSync(f, "utf8");
  for (const [key, value] of Object.entries(map)) {
    const line = `    <string name="${key}">${esc(value)}</string>`;
    const re = new RegExp(`^ *<string name="${key}">.*</string>$`, "m");
    xml = re.test(xml) ? xml.replace(re, () => line) : xml.replace("</resources>", line + "\n</resources>");
  }
  fs.writeFileSync(f, xml);
}
