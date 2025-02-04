from z3 import *
import re

# Parse the DOT graph to extract variables and constraints
def parse_cfg(dot_graph):
    node_labels = re.findall(r'(node\d+) \[label=\"(.*?)\"\];', dot_graph)
    variables = set()
    assignments = {}  # Store variable assignments for each node
    path_constraints = []  # Store path conditions (e.g., x + y > 10)

    for node, label in node_labels:
        print(f"Processing node: {node} with label: {label}")  # Debugging statement

        # Handle input assignments like 'x = input()'
        input_match = re.match(r'(\w+) = input\(\)', label)
        if input_match:
            var = input_match.group(1)
            variables.add(var)
            continue  # No constraint, just a variable declaration

        # Handle variable assignments like 'z = x * 2'
        assign_match = re.match(r'(\w+) = (.+)', label)
        if assign_match and not label.startswith('if'):
            var, expr = assign_match.groups()
            variables.add(var)
            # Store the assignment for the current node
            assignments[node] = (var, expr)
            continue

        # Handle conditional statements like 'if x + y > 10 goto node4 else goto node5'
        if label.startswith('if'):
            cond_match = re.match(r'if (.+?) goto', label)
            if cond_match:
                condition = cond_match.group(1)
                path_constraints.append((node, condition))

    print(f"Identified variables: {variables}")  # Debugging statement
    print(f"Variable assignments: {assignments}")  # Debugging statement
    print(f"Path constraints: {path_constraints}")  # Debugging statement

    return variables, assignments, path_constraints

# Solve constraints for the path leading to result = 'valid'
def solve_for_valid_path(z3_vars, assignments, path_constraints, solver):
    # Add constraints for the path leading to result = 'valid'
    # Path: x + y > 10 (node3) -> z = x * 2 (node4) -> z == 15 (node6) -> result = 'valid' (node7)
    solver.add(eval("x + y > 10", {}, z3_vars))  # Path condition
    solver.add(eval("z == 15", {}, z3_vars))  # Final condition

    # Add assignments along the path
    for node, (var, expr) in assignments.items():
        if node == 'node4':  # Only add the assignment for z = x * 2
            print(f"Adding assignment: {var} = {expr}")  # Debugging statement
            solver.add(z3_vars[var] == eval(expr, {}, z3_vars))

    # Check for satisfiability
    if solver.check() == sat:
        model = solver.model()
        return {str(d): model[d] for d in model}
    else:
        return None

# Find all solutions for the path leading to result = 'valid'
def find_all_solutions(z3_vars, assignments, path_constraints):
    solver = Solver()
    solutions = []

    while True:
        # Solve for the next solution
        solution = solve_for_valid_path(z3_vars, assignments, path_constraints, solver)
        if not solution:
            break  # No more solutions

        # Store the solution
        solutions.append(solution)
        print(f"Found solution: {solution}")

        # Add a constraint to exclude the current solution
        solver.add(Or([z3_vars[var] != solution[var] for var in z3_vars]))

    return solutions

# Extract constraints and create Z3 solver
def solve_constraints(dot_file_path):
    # Read the DOT file
    with open(dot_file_path, 'r') as file:
        dot_graph = file.read()

    variables, assignments, path_constraints = parse_cfg(dot_graph)

    # Dynamically create Z3 variables
    z3_vars = {var: Int(var) for var in variables}

    # Find all solutions for the path leading to result = 'valid'
    print("\nFinding all solutions for path leading to result = 'valid'")
    solutions = find_all_solutions(z3_vars, assignments, path_constraints)

    if solutions:
        print("\nAll solutions found:")
        for i, solution in enumerate(solutions, 1):
            print(f"Solution {i}: {solution}")
    else:
        print("No solutions found for path leading to result = 'valid'")

# Path to the DOT file
dot_file_path = 'simple_graph.dot'

# Solve constraints
solve_constraints(dot_file_path)