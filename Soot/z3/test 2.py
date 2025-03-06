from z3 import *

solver = Solver()

# Example variables
r2 = String('r2')
z0 = Int('z0')
b4 = Int('b4')
i0 = Int('i0')
i1 = Int('i1')

# Add constraints (modify these based on your real problem)
solver.add(r2 == "+", z0 == 1, b4 == 0)  # Already constrained
parameters = {"r2": r2, "z0": z0, "b4": b4, "i0": i0, "i1": i1}

# Find multiple solutions
while solver.check() == sat:
    model = solver.model()
    
    # Print the solution
    print("Solution found:")
    for param_name, z3_var in parameters.items():
        value = model[z3_var] if z3_var in model else None
        print(f"{param_name} = {value if value is not None else 'No restriction on this value'}")
    
    print("-" * 50)
    
    # Block only the assigned variables
    solver.add(Or([z3_var != model[z3_var] for z3_var in model]))
    
print("No more solutions.")
