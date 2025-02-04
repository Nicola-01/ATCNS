from z3 import *

# Declare symbolic variables
x = Int('x')
y = Int('y')

# Define constraints for each node
node_constraints = {
    'A': [x == y + 2],          # Assignment: x = y + 2
    'B_true': [x > 5],          # Condition: if x > 5 (True branch)
    'B_false': [x <= 5],        # Condition: if x <= 5 (False branch)
    'D_true': [y < 3],          # Condition: if y < 3 (True branch)
    'D_false': [y >= 3],        # Condition: if y >= 3 (False branch)
    'C': [],                    # No additional constraints
    'E': [],                    # Final node after C
    'F': [],                    # Final node after D_true
    'G': []                     # Final node after D_false
}

# Define CFG paths
paths = [
    ['A', 'B_true', 'C', 'E'],         # Path 1: A → B (True) → C → E
    ['A', 'B_false', 'D_true', 'F'],   # Path 2: A → B (False) → D (True) → F
    ['A', 'B_false', 'D_false', 'G']   # Path 3: A → B (False) → D (False) → G
]

# Function to block the current model to find new solutions
def block_model(solver, model, variables):
    block_clause = Or([var != model[var] for var in variables])
    solver.add(block_clause)

# Function to explore a path and find up to 5 satisfying inputs
def explore_path(path, max_solutions=5):
    solver = Solver()
    print(f"\nExploring Path: {' → '.join(path)}")

    # Add all constraints from the nodes in the path
    for node in path:
        for constraint in node_constraints.get(node, []):
            solver.add(constraint)

    # Find up to `max_solutions` models
    solutions_found = 0
    variables = [x, y]  # Variables we're interested in

    while solutions_found < max_solutions and solver.check() == sat:
        model = solver.model()
        print(f"Solution {solutions_found + 1}: {model}")
        block_model(solver, model, variables)  # Block the current model
        solutions_found += 1

    if solutions_found == 0:
        print("UNSAT - No valid inputs.")

# Explore all paths
for path in paths:
    explore_path(path, max_solutions=5)