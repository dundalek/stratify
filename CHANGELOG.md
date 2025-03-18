# Changelog

## [master](https://github.com/dundalek/stratify/compare/v0.4.0...master) (unreleased)

## [0.4.0](https://github.com/dundalek/stratify/compare/v0.3.0...v0.4.0) (2025-03-18)

New features:
- Implemented additional code metrics in the report:
  - Lakos component dependency
  - Dowalil visibility
  - LCOM4 cohesion
  - Experimental Module Depth metric (inspired by Osterhout)
- Support to view metrics delta to compare a system between two versions using `--metrics-delta` option
- Added 3D Code Galaxy visualization using `dep-tree` renderer

## [0.3.0](https://github.com/dundalek/stratify/compare/v0.2.0...v0.3.0) (2025-01-30)

Main features:
- Added support for 3D Code City visualization using CodeCharta
- Generate infrastructure maps from Infrastructure as Code specs (Pulumi)
- Show nodes in dependency graph colored based on code coverage score

Smaller improvements:
- Added `--insert-namespace-node` option to group vars mixed among namespaces under a node
- Use node labels for Graphviz conversion
- Improved error messages

## [0.2.0](https://github.com/dundalek/stratify/compare/v0.1.0...v0.2.0) (2024-09-16)

New features:
- Graphviz support for interactive visualization of outputs produced by other tools
- Architecture maps visualization based on C4 models
- Code metrics calculation and visual reports
- Experiments to use graph queries (Datalog) for architecture checks and dependency rules

## 0.1.0 (2024-07-29)

Initial release - Extract Clojure/Script code as DGML graph
