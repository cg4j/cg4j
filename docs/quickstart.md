# Quickstart

Install `cg4j` for the current user on Linux, macOS, or WSL. No `sudo` access is required.

```bash
curl -fsSL https://cg4j.net/install.sh | bash
```

> Note: If `cg4j` is already installed, `install.sh` will prompt to uninstall the current installation and then exit. Run the installer again if you want to reinstall `cg4j`.

Verify installation:

```bash
cg4j --help
```

## Usage

After installation:

```bash
# Basic usage - outputs to callgraph.csv
cg4j -j myapp.jar

# With dependencies and custom output
cg4j -j myapp.jar -o output.csv -d lib/

# Use the ASM engine
cg4j -j myapp.jar --engine asm

# Suppress info/progress logs
cg4j -j myapp.jar -q
```

## Options

- `-j, --app-jar=<file>` - JAR file to analyze (required)
- `-o, --output=<file>` - Output CSV file (default: `callgraph.csv`)
- `-d, --deps=<dir>` - Directory containing dependency JAR files
- `--include-rt` - Include Java runtime in call graph analysis (default: `true`)
- `--engine` - Call graph engine: `wala` or `asm` (default: `wala`)
- `--exclusions` - Exclusion patterns file for the ASM engine; see [`default-exclusions.txt`](https://github.com/cg4j/cg4j/blob/master/src/main/resources/default-exclusions.txt)
- `-q, --quiet` - Suppress info/progress logs (errors only)
- `-h, --help` - Show help message
- `-v, --version` - Show version information

## Docker

### Pull and Run

Pull the latest published image:

```bash
docker pull cg4j/cg4j:latest
```

Show the available CLI options. The Docker container supports the same options as the local
`cg4j` installation shown above.

```bash
docker run --rm cg4j/cg4j:latest --help
```

Run an analysis by mounting your current directory into the container for input and output:

```bash
docker run --rm \
  -v $(pwd):/input:ro \
  -v $(pwd):/output \
  cg4j/cg4j:latest -j /input/myapp.jar -o /output/callgraph.csv
```

The `/input` volume mount lets the container read the application JAR and any dependency JARs
from your machine. The `/output` volume mount writes the generated call graph CSV back to your
machine.
