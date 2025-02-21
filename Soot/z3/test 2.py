from z3 import *

# Creazione della variabile stringa
r2 = Int('r2')

# Creazione della funzione hashCode
hashCode = Function('hashCode', StringSort(), IntSort())

# Creazione del solver
solver = Solver()

# Aggiunta di vincoli complessi
solver.add(r2 > 23)

# Verifica se il vincolo è soddisfacibile
if solver.check() == sat:
    model = solver.model()
    print("r2 =", model[r2])
else:
    print("Nessuna soluzione trovata.")