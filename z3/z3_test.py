from z3 import *

# Define Z3 variables
x = Int('x')
y = Int('y')
z = Int('z')

# Create a solver instance
solver = Solver()

# Condition 1: if x + y > 10
cond1 = x + y > 10

# Branch 1: if True -> z = x * 2
branch1 = Implies(cond1, z == x * 2)

# Branch 2: if False -> z = y * 3
branch2 = Implies(Not(cond1), z == y * 3)

# Condition 2: z == 15 to reach 'valid'
cond2 = z == 15

# Add constraints to the solver
solver.add(branch1, branch2, cond2)

# Find up to 5 solutions
solutions = []
while len(solutions) < 5 and solver.check() == sat:
    model = solver.model()
    solution = (model[x].as_long(), model[y].as_long(), model[z].as_long())
    solutions.append(solution)
    
    # Add constraint to find a different solution next time
    solver.add(Or(x != solution[0], y != solution[1]))

# Display solutions
if solutions:
    for idx, (x_val, y_val, z_val) in enumerate(solutions, 1):
        print(f"Solution {idx}: x = {x_val}, y = {y_val}, z = {z_val}, Result: valid")
else:
    print("No solution leads to 'valid'")