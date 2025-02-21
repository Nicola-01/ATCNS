from z3 import *

# Declare variables
parametes = {}

parametes.update({"i0": Int('i0')})
parametes.update({"i1": Int('i1')})
parametes.update({"r2": String('r2')})

hashCode = Function('hashCode', StringSort(), IntSort())

b4 = Int('b4')

# Create a solver instance
solver = Solver()

# Constraint: r2 must be "*"
# solver.add(list[2] == StringVal("*"))
solver.add(b4 == 1)

# condition = "list[1] == 3"
# solver.add(eval(condition))

condition = "parametes.get('i1') == 3"
solver.add(eval(condition))

condition = "parametes.get('r2') == StringVal(\"*\")"
solver.add(eval(condition))

# Check if the constraints are satisfiable
if solver.check() == sat:
    model = solver.model()
    print(f"i0 = {model[parametes.get('i0')]}")
    print(f"i1 = {model[parametes.get('i1')]}")
    print(f"r2 = {model[parametes.get('r2')]}")
else:
    print("No solution found.")