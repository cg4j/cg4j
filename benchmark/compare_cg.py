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


def parse_edges_set(csv_path: Path) -> Tuple[Set[Tuple[str, str]], str]:
  """
  Parse a call graph CSV and return edge set for comparison.

  Args:
    csv_path: Path to the CSV file

  Returns:
    Tuple of (edges_set, filename)
    - edges_set: Set of (source, target) tuples
    - filename: Name of the CSV file

  Raises:
    FileNotFoundError: If the CSV file doesn't exist
    ValueError: If the CSV format is invalid

  Note:
    Edges with same (source, target) are considered identical.
  """
  if not csv_path.exists():
    raise FileNotFoundError(f"File not found: {csv_path}")

  edges: Set[Tuple[str, str]] = set()

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

      # Parse rows and collect edges
      for row in reader:
        source = row['source_method'].strip()
        target = row['target_method'].strip()

        if source and target:
          edges.add((source, target))

  except csv.Error as e:
    raise ValueError(f"CSV parsing error in {csv_path}: {e}")

  return edges, csv_path.name


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


def format_edges_diff_table(cg1_edges: Tuple[Set[Tuple[str, str]], str],
                            cg2_edges: Tuple[Set[Tuple[str, str]], str]) -> str:
  """
  Format Venn diagram-style comparison table for edges only.

  Args:
    cg1_edges: Tuple of (edges_set, filename) for first call graph
    cg2_edges: Tuple of (edges_set, filename) for second call graph

  Returns:
    Formatted table string showing edge set intersection and differences
  """
  edges1, name1 = cg1_edges
  edges2, name2 = cg2_edges

  # Calculate set operations
  edges_only_cg1 = edges1 - edges2
  edges_intersection = edges1 & edges2
  edges_only_cg2 = edges2 - edges1
  total_unique = len(edges_only_cg1) + len(edges_intersection) + len(edges_only_cg2)

  # Calculate percentages
  if total_unique == 0:
    pct_only_cg1 = 0.0
    pct_intersection = 0.0
    pct_only_cg2 = 0.0
  else:
    pct_only_cg1 = (len(edges_only_cg1) / total_unique) * 100
    pct_intersection = (len(edges_intersection) / total_unique) * 100
    pct_only_cg2 = (len(edges_only_cg2) / total_unique) * 100

  # Prepare table data
  headers = ["Category", "Count", "Percentage"]
  table_data = [
    [f"Only in {name1}", format_number(len(edges_only_cg1)), f"{pct_only_cg1:.1f}%"],
    ["Intersection (Both)", format_number(len(edges_intersection)), f"{pct_intersection:.1f}%"],
    [f"Only in {name2}", format_number(len(edges_only_cg2)), f"{pct_only_cg2:.1f}%"],
    ["Total Unique", format_number(total_unique), "100.0%"]
  ]

  # Create table with grid format
  table = tabulate(table_data, headers=headers, tablefmt="grid")

  # Add title
  title = "Edges Set Comparison"
  separator = "=" * len(table.split('\n')[0])
  
  return f"\n{title}\n{separator}\n{table}\n"


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

  parser.add_argument(
    '--count',
    action='store_true',
    help='Display node and edge count comparison table (default behavior)'
  )

  parser.add_argument(
    '--diff',
    action='store_true',
    help='Display edge set intersection analysis (Venn diagram)'
  )

  args = parser.parse_args()

  try:
    # Determine which output to show
    # Default behavior (no flags): show comparison table
    show_comparison = args.count or (not args.count and not args.diff)
    show_diff = args.diff

    if show_comparison:
      # Parse call graphs for comparison table
      print("Parsing call graphs...", file=sys.stderr)
      cg1_stats = parse_call_graph(args.cg1)
      cg2_stats = parse_call_graph(args.cg2)

      # Display comparison table
      comparison = format_comparison_table(cg1_stats, cg2_stats)
      print(comparison)

    if show_diff:
      # Parse edges for Venn diagram comparison
      if not show_comparison:
        print("Parsing call graphs...", file=sys.stderr)
      
      cg1_edges = parse_edges_set(args.cg1)
      cg2_edges = parse_edges_set(args.cg2)

      # Display edges diff table
      diff_table = format_edges_diff_table(cg1_edges, cg2_edges)
      print(diff_table)

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
