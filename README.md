# Java Powersort

Java implementation and experiment harness for Powersort variants (V0-V5), with benchmark and profiling utilities used in the project.

## Repository Scope

This repository is intentionally kept focused on:

- Java source files for algorithms and experiments
- Minimal project metadata
- Usage documentation

Generated artifacts (CSV outputs, PDFs, IDE metadata, report folders, zip archives) are excluded from version control.

## Source Files

- `sort.java`: primary Powersort implementation (V0-V5)
- `naivesort.java`: educational/reference variant
- `BenchmarkSuite.java`: full benchmark campaign harness
- `PilotBenchmark.java`: smaller pilot benchmark harness
- `PowersortTest.java`: correctness + differential checks
- `profile.java`: overhead profiling utility

## Quick Start

Compile everything:

```bash
javac sort.java naivesort.java BenchmarkSuite.java PilotBenchmark.java PowersortTest.java profile.java
```

Run key workflows:

```bash
java PowersortTest
java BenchmarkSuite
java PilotBenchmark pilot_results.csv
java profile all 100000 random 10
```

## Usage Guide

See `usage.txt` for complete command examples for algorithm runs, validation, profiling, and benchmark workflows.

## Reference

This implementation is based on Powersort ideas discussed by Sebastian Wild and related material:

- https://colab.research.google.com/drive/13jJDr7dcEz2Ub48TzOT-DaJYCNc5UduR
