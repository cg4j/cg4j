.PHONY: help all build test install uninstall clean version

# Default target - show help
help:
	@echo "cg4j - Call Graph Generator for Java"
	@echo ""
	@echo "Available targets:"
	@echo "  make build      - Build the JAR with Maven"
	@echo "  make test       - Run tests"
	@echo "  make install    - Install cg4j to ~/.local/bin"
	@echo "  make uninstall  - Remove cg4j from system"
	@echo "  make clean      - Clean build artifacts"
	@echo "  make version    - Show project version"
	@echo "  make all        - Build and test"

all: build test

build:
	mvn clean package

test:
	mvn test

install:
	@chmod +x scripts/install.sh
	./scripts/install.sh

uninstall:
	@chmod +x scripts/uninstall.sh
	@./scripts/uninstall.sh

clean:
	mvn clean

version:
	@echo -n "cg4j version: "
	@grep -A 2 "<artifactId>cg4j-cli</artifactId>" pom.xml | grep "<version>" | sed 's/.*<version>\(.*\)<\/version>/\1/' | head -1
