import re
from z3 import *
import networkx as nx
from networkx.drawing.nx_agraph import read_dot

def parse_dot_file(file_path):
    """
    Parse the DOT file and return a NetworkX graph.
    """
    return read_dot(file_path)

def extract_variables_and_constraints(graph):
    """
    Extract variables and constraints from the graph.
    """
    variables = set()
    constraints = []

    # Variables to exclude from analysis
    excluded_variables = {"result"}

    for node, data in graph.nodes(data=True):
        label = data.get('label', '')
        # Extract variable assignments (e.g., "x = input()" or "z = x * 2")
        match = re.match(r'(\w+)\s*=\s*(.+)', label)
        if match:
            var = match.group(1)
            if var not in excluded_variables:  # Exclude unwanted variables
                variables.add(var)

    for node, data in graph.nodes(data=True):
        label = data.get('label', '')
        # Handle conditional statements like 'if x + y > 10 goto node4 else goto node5'
        if label.startswith('if'):
            cond_match = re.match(r'if (.+?) goto', label)
            if cond_match:
                condition = cond_match.group(1)
                constraints.append(condition)

    return variables, constraints

def solve_path(graph, variables, constraints, path):
    """
    Solve for variable values along a given path using Z3.
    """
    solver = Solver()
    var_map = {var: Int(var) for var in variables}

    # Add constraints for the path
    for i in range(len(path) - 1):
        u, v = path[i], path[i + 1]
        for constraint in constraints:
            # Replace variable names with Z3 variables
            constraint_expr = constraint
            for var in var_map:
                constraint_expr = constraint_expr.replace(var, f'var_map["{var}"]')
            solver.add(eval(constraint_expr, {"var_map": var_map}))

    # Check satisfiability
    if solver.check() == sat:
        model = solver.model()
        return {var: model[var_map[var]] for var in variables}
    else:
        return None

def explore_paths(graph, variables, constraints):
    """
    Explore all paths in the CFG and solve for variable values.
    """
    start_node = None
    end_nodes = []

    # Find start and end nodes
    for node in graph.nodes():
        if graph.in_degree(node) == 0:
            start_node = node
        if graph.out_degree(node) == 0:
            end_nodes.append(node)

    if not start_node or not end_nodes:
        raise ValueError("Invalid CFG: Start or end node not found.")

    # Generate all paths from start to end nodes
    all_paths = []
    for end_node in end_nodes:
        paths = nx.all_simple_paths(graph, start_node, end_node)
        all_paths.extend(paths)

    # Solve for each path
    results = []
    for path in all_paths:
        result = solve_path(graph, variables, constraints, path)
        if result:
            results.append((path, result))

    return results

def main(dot_file):
    """
    Main function to read the DOT file, extract constraints, and explore paths.
    """
    graph = parse_dot_file(dot_file)
    print("Graph nodes and edges:")
    print(graph.nodes(data=True))
    print(graph.edges(data=True))

    variables, constraints = extract_variables_and_constraints(graph)
    print("Variables to analyze:", variables)
    print("Constraints:", constraints)

    results = explore_paths(graph, variables, constraints)

    for path, result in results:
        print(f"Path: {' -> '.join(path)}")
        print(f"Variable values: {result}")
        print()

if __name__ == "__main__":
    dot_file = "simple_graph.dot"  # Replace with your DOT file path
    main(dot_file)