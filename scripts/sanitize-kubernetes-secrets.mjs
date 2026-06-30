#!/usr/bin/env node

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  input += chunk;
});
process.stdin.on("end", () => {
  const parsed = JSON.parse(input);
  const items = parsed.items || (parsed.kind === "Secret" ? [parsed] : []);
  const sanitized = items.map((secret) => {
    const data = secret.data || {};
    return {
      apiVersion: secret.apiVersion,
      kind: secret.kind,
      metadata: {
        name: secret.metadata?.name,
        namespace: secret.metadata?.namespace,
        creationTimestamp: secret.metadata?.creationTimestamp,
      },
      type: secret.type,
      sanitizedData: Object.fromEntries(Object.entries(data).map(([key, value]) => {
        const decodedLength = Buffer.from(value, "base64").length;
        return [key, { present: true, decodedLength }];
      })),
    };
  });
  process.stdout.write(JSON.stringify({ items: sanitized }, null, 2));
  process.stdout.write("\n");
});
