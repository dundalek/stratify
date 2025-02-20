# Glean

[Glean](https://glean.software/)

- has indexers to load facts from multiple sources, includes SCIP indexers
- Angle query language similar to Datalog, same language for defining rules and queries


Start the Glean container:
```
docker compose up
```

Open shell inside container:
```
docker exec -it glean-demo /bin/bash
```

### Index sample project

Extract facts from sample JS sources using [Flow](https://glean.software/docs/indexer/flow/) indexer:

```
mkdir /data/flow
flow glean /data/sample-js/src --all --output-dir /data/flow --write-root PREFIX
```

Load extracted facts into glean DB:

```
glean create --db-root /glean-demo/db --schema /glean-demo/schema/source --db sample-js/0 /data/flow/*.json
```

### Queries

Start the glean shell to execute queries with:

```
glean shell --db-root /glean-demo/db --schema /glean-demo/schema/source --db sample-js/0
```

flow predicates

    flow.Declaration _

    flow.DeclarationInfo _

    flow.LocalDeclarationReference _



binding variable to extract nested data

    D \
    where \
    flow.Declaration { name = D }


declarations

    {SourceName, Module} \
    where \
    flow.DeclarationInfo { declaration = SourceDecl, span = {just = { module = Module }}}; \
    { name = SourceName } = SourceDecl;


references

    {TargetName, Module} \
    where \
    flow.LocalDeclarationReference { declaration = { name = TargetName }, loc = { module = Module } }


references inside declarations

    {SourceName, TargetName} \
    where \
    flow.DeclarationInfo { declaration = { name = SourceName }, span = DeclarationLocation }; \
    {just = { module = Module, span = DeclarationSpan }} = DeclarationLocation; \
    flow.LocalDeclarationReference { declaration = { name = TargetName }, loc = ReferenceLocation }; \
    { module = Module, span = ReferenceSpan } = ReferenceLocation; \
    src.ByteSpanContains { DeclarationSpan, ReferenceSpan };


misc flow predicates

    flow.ModuleContains _

    flow.DeclarationLocation _

    codemarkup.flow.FlowContainsParentEntity _


higher-level codemarkup predicates

    codemarkup.ContainsParentEntity _

    codemarkup.EntityReferences _

    codemarkup.FileEntityLocations _

    codemarkup.EntityLocation _

    codemarkup.ResolveLocation _


no results for the js sample

    codemarkup.ReferencingEntity _

    codemarkup.EntityVisibility _
