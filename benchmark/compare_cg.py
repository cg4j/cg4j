#!/usr/bin/env python3
"""
Call Graph Comparison Tool for CG4j

Compares two call graph CSV files and displays statistics in a formatted table.
"""

import argparse
import csv
import sys
from pathlib import Path
from typing import Tuple, Set

try:
  from tabulate import tabulate
except ImportError:
  print("Error: tabulate library not found.", file=sys.stderr)
  print("Install it with: pip install -r requirements.txt", file=sys.stderr)
  sys.exit(1)


def parse_call_graph(csv_path: Path) -> Tuple[int, int, str]:
  """
  Parse a call graph CSV file and extract statistics.

  Args:
    csv_path: Path to the CSV file

  Returns:
    Tuple of (node_count, edge_count, filename)

  Raises:
    FileNotFoundError: If the CSV file doesn't exist
    ValueError: If the CSV format is invalid
  """
  if not csv_path.exists():
    raise FileNotFoundError(f"File not found: {csv_path}")

  nodes: Set[str] = set()
  edge_count = 0

  try:
    with open(csv_path, 'r', encoding='utf-8') as f:
      reader = csv.DictReader(f)

      # Validate CSV headers
      if reader.fieldnames is None:
        raise ValueError(f"Invalid CSV format: No headers found in {csv_path}")
      
      if 'source_method' not in reader.fieldnames or 'target_method' not in reader.fieldnames:
        raise ValueError(
          f"Invalid CSV format. Expected columns: 'source_method', 'target_method'. "
          f"Found: {reader.fieldnames}"
        )

      # Parse rows and collect statistics
      for row in reader:
        source = row['source_method'].strip()
        target = row['target_method'].strip()

        if source and target:
          nodes.add(source)
          nodes.add(target)
          edge_count += 1

  except csv.Error as e:
    raise ValueError(f"CSV parsing error in {csv_path}: {e}")

  return len(nodes), edge_count, csv_path.name


def format_number(num: int) -> str:
  """Format a number with thousands separators."""
  return f"{num:,}"


def calculate_diff(val1: int, val2: int) -> str:
  """
  Calculate the difference between two values.

  Returns:
    Formatted string showing absolute and percentage difference
  """
  diff = val2 - val1
  sign = "+" if diff > 0 else ""

  if val1 == 0:
    if val2 == 0:
      return "0 (0.0%)"
    else:
      return f"{sign}{format_number(diff)} (∞%)"

  percentage = (diff / val1) * 100
  return f"{sign}{format_number(diff)} ({sign}{percentage:.1f}%)"


def format_comparison_table(cg1_stats: Tuple[int, int, str], 
                            cg2_stats: Tuple[int, int, str]) -> str:
  """
  Format comparison statistics into a table.

  Args:
    cg1_stats: Tuple of (nodes, edges, filename) for first call graph
    cg2_stats: Tuple of (nodes, edges, filename) for second call graph

  Returns:
    Formatted table string
  """
  nodes1, edges1, name1 = cg1_stats
  nodes2, edges2, name2 = cg2_stats

  # Prepare table data
  headers = ["Metric", "CG1", "CG2", "Difference"]
  table_data = [
    ["File", name1, name2, "-"],
    ["Nodes", format_number(nodes1), format_number(nodes2), calculate_diff(nodes1, nodes2)],
    ["Edges", format_number(edges1), format_number(edges2), calculate_diff(edges1, edges2)]
  ]

  # Create table with grid format
  table = tabulate(table_data, headers=headers, tablefmt="grid")

  # Add title
  title = "Call Graph Comparison"
  separator = "=" * len(table.split('\n')[0])
  
  return f"\n{title}\n{separator}\n{table}\n"


def main():
  """Main entry point for the script."""
  parser = argparse.ArgumentParser(
    description="Compare two call graph CSV files and display statistics.",
    formatter_class=argparse.RawDescriptionHelpFormatter,
    epilog="""
Examples:
  # Compare two call graphs
  python compare_cg.py ../cg_okhttp_w_deps_wala.csv ../cg_okhttp_w_deps_asm.csv

  # Using absolute paths
  python compare_cg.py /path/to/cg1.csv /path/to/cg2.csv

Expected CSV format:
  source_method,target_method
  package/Class.method:(descriptor),package/Class.method:(descriptor)
    """
  )

  parser.add_argument(
    'cg1',
    type=Path,
    help='First call graph CSV file'
  )

  parser.add_argument(
    'cg2',
    type=Path,
    help='Second call graph CSV file'
  )

  args = parser.parse_args()

  try:
    # Parse both call graphs
    print("Parsing call graphs...", file=sys.stderr)
    cg1_stats = parse_call_graph(args.cg1)
    cg2_stats = parse_call_graph(args.cg2)

    # Display comparison table
    comparison = format_comparison_table(cg1_stats, cg2_stats)
    print(comparison)

    sys.exit(0)

  except FileNotFoundError as e:
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)

  except ValueError as e:
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)

  except Exception as e:
    print(f"Unexpected error: {e}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
  main()
