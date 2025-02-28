import re
import networkx as nx
import pygraphviz as pgv
from z3 import *

# Regular expression pattern for matching the parameter definition in the node label.
PARAM_PATTERN = re.compile(
        r'^\$?(\w+)\s*\(([^)]+)\)\s*=\s*\(android\.os\.Bundle\)\s*\$?\w+\.get\w+\("([^"]+)"\)'
    )

def parse_dot_file(dot_path):
    """
    Reads the DOT file and returns a dictionary of NetworkX directed graphs.
    Each subgraph in the DOT file is assumed to represent a distinct path.
    """
    # Load the DOT file using PyGraphviz.
    A = pgv.AGraph(dot_path)
    # Convert each subgraph to a NetworkX DiGraph and return as a dictionary.
    return {sub.name: nx.DiGraph(sub) for sub in A.subgraphs()}

def parse_parameters_from_subgraph(graph):
    """
    Scans the given subgraph for nodes whose label matches the pattern:
      <variable> (<returned_type>) = (android.os.Bundle) <object>.<bundle_get_method>("<parameter_name>")
    and returns a dictionary mapping variable names (without any '$') to Z3 variables
    of the appropriate type.
    """
    params = {}
    # Iterate over each node in the graph.
    for node in graph.nodes(data=True):
        label = node[1].get('label', '').strip()
        # Check if the label matches the expected parameter pattern.
        match = PARAM_PATTERN.match(label)
        if match:
            variable, returned_type, param_name = match.groups()
            # Remove any leading '$' from the variable name.
            variable = variable.lstrip('$')
            # Create a Z3 variable of the appropriate type.
            if returned_type.lower() == "int":
                params[variable] = Int(variable)
            elif "string" in returned_type.lower():
                params[variable] = String(variable)
            elif "bool" in returned_type.lower():
                params[variable] = Bool(variable)
            elif "float" in returned_type.lower():
                params[variable] = Real(variable)
            else:
                # If the type is not recognized, default to an integer.
                params[variable] = Int(variable)

    return params

def add_constraints_from_subgraph(graph, solver, parameters, eval_context):
    """
    Iterates over nodes in the graph to add constraints to the Z3 solver.
    
    This function handles two types of nodes:
      - 'if' nodes: extract the condition and add it to the solver,
        negating the condition if any outgoing edge is labeled "false".
      - Simple assignment nodes: add constraints like `var == value`.
    
    Parameters:
      graph: The NetworkX graph representing a path.
      solver: The Z3 solver instance to which constraints are added.
      parameters: A dictionary of Z3 variables extracted from parameter definitions.
      eval_context: A context dictionary for eval() containing variables and functions.
    """    
    condition_parameters = []

    # Iterate over each node in the graph.
    for node in graph.nodes(data=True):
        node_id = node[0]
        label = node[1].get('label', '').strip()
        
        # Skip nodes that are parameter definitions.
        if PARAM_PATTERN.match(label):
            continue

        # Process nodes that represent conditional statements.
        if label.startswith('if '):
            # Extract the condition part of the label (between "if " and "goto").
            match = re.search(r'if\s+(.+?)\s+goto', label)
            if match:
                condition_str = match.group(1)
                # Remove any '$' characters so that variable names match those in our context.
                condition_str = condition_str.replace("$", "")
                condition_param = condition_str.split("==")[0]
                if condition_param not in condition_parameters:
                    condition_parameters.append(condition_param)    
                #print(condition_parameters)
                # Check outgoing edges: if any edge has the label "false", negate the condition.
                for successor in graph.successors(node_id):
                    edge_data = graph.get_edge_data(node_id, successor)
                    if edge_data and edge_data.get('label', '') == "false":
                        condition_str = f"Not({condition_str})"
                # Evaluate the condition string in the provided context and add it to the solver.
                try:
                    solver.add(eval(condition_str, {}, eval_context))
                except Exception as e:
                    print(f"Error evaluating condition '{condition_str}':", e)
                    
        # Process simple assignment nodes (e.g., "i2 = 0").
        elif ' = ' in label:
            parts = label.split(' = ')
            if len(parts) == 2:
                variable, value = parts
                # Clean up variable name by removing '$' and extra whitespace.
                variable = variable.replace("$", "").strip()
                value = value.strip()
                # Process only simple tokens (one word on each side).
                if len(variable.split()) == 1 and len(value.split()) == 1:
                    if variable not in parameters and variable in condition_parameters:
                        # Infer the variable type based on the value.
                        try:
                            int(value)
                            parameters[variable] = Int(variable)
                        except:
                            try:
                                float(value)
                                parameters[variable] = Real(variable)
                            except:
                                if value.lower() in ["true", "false"]:
                                    parameters[variable] = Bool(variable)
                                else:
                                    parameters[variable] = String(variable)
                    # Construct the constraint string to enforce that the variable equals the value.
                    constraint_str = f"{variable} == {value}"
                    # Evaluate and add the assignment constraint.
                    try:
                        solver.add(eval(constraint_str, {}, eval_context))
                    except Exception as e:
                        print(f"Error evaluating assignment constraint '{constraint_str}':", e)

    print("All parameters: ", parameters)
# Main logic

# Load the DOT file and extract subgraphs (paths).
subgraphs = parse_dot_file("paths.dot")

# Process each subgraph (each representing a different path).
for i in range(1, len(subgraphs)):
    pathName = f"path_{i}"
    print(f"Solution for {pathName}")

    # Automatically retrieve intent-related parameter definitions from the subgraph.
    parameters = parse_parameters_from_subgraph(subgraphs[pathName])
    print("Detected intent-related parameters:", parameters)

    # Initialize a new Z3 solver instance.
    solver = Solver()

    # Build the evaluation context so that eval() can correctly resolve parameters and functions.
    eval_context = {"parameters": parameters, "Not": Not}
    eval_context.update(parameters)

    # Add constraints extracted from the subgraph nodes to the solver.
    add_constraints_from_subgraph(subgraphs[pathName], solver, parameters, eval_context)

    ############ DEBUG #################
    print("################################\nConstraints in the solver:")
    for assertion in solver.assertions():
        print(assertion)
    print("################################")
    ####################################

    # Check if the constraints are satisfiable.
    if solver.check() == sat:
        model = solver.model()
        # If a solution is found, print the model values for each parameter.
        for param, var in parameters.items():
            val = model.evaluate(var, model_completion=True)
            print(f"{param} = {val}")
    else:
        print("No solution found.")
    print("-" * 50)
