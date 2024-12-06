# Pulumi sample

Simple Pulumi example with a bucket and a lambda handler.

Below are commands that were used to obtain test data.

### 1) Preview from empty stack

Output stack preview without deploying:

```
pulumi preview --json > ../sample-preview-creates.json
```

### 2) Deployed stack export

Then deploy with:

```
pulumi up
```

And export stack:

```
pulumi stack export > ../sample-stack-export.json
```

### 3) Preview with updates

Change the body of `docsHandler` in `index.ts`, the preview output will include `update` step:

```
pulumi preview --show-sames --json > ../sample-preview-update.json
```

### 4) Preview with deletes

Remove `docsHandler` in `index.ts`, the preview output will include `delete` steps:

```
pulumi preview --show-sames --json > ../sample-preview-deletes.json
```
