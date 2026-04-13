# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Add a standalone Maven Central installer and split development install scripts (#40)

### Changed

- Adopt Google Java formatting with Spotless, pre-commit, and CI checks ([#50](https://github.com/cg4j/cg4j/pull/50))
- Add README badges for license, build status, and Maven release visibility (#43)
- Use lowercase `-v` as the CLI version flag (with `--version`) (#44)
- Improve public API Javadocs so release builds complete without warnings ([#49](https://github.com/cg4j/cg4j/pull/49))

### Deprecated

### Removed

### Fixed

- Improve ASM engine soundness to better match WALA edge sets (#45)

### Security

## [v0.1.0] - 2026-03-22

### Added
- Dual call graph engine: IBM WALA RTA and custom ASM-based RTA algorithm
- Call graph generation from JAR files using Rapid Type Analysis (RTA)
- CSV output format with caller-callee method pairs
- CLI interface powered by picocli with options for JAR, output, dependencies, and quiet mode
- Lambda factory and static initializer (clinit) support in ASM engine
- Docker and Docker Compose support for containerized call graph analysis
- Comprehensive test suite with 95% line coverage enforced by JaCoCo
- Parallel test execution using JUnit 5 Jupiter dynamic strategy
- CI/CD pipeline with GitHub Actions for automated build and test
- Benchmark tool for comparing and diffing call graph CSV outputs

### Changed

### Deprecated

### Removed

### Fixed

### Security
