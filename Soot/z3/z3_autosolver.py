import re
import networkx as nx
import pygraphviz as pgv
from z3 import Int, String, Bool, Real, Solver, sat, Not

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


def infer_type(variable, value):
    
    # Check if the value is a boolean
    if value.lower() == "true" or value.lower() == "false":
        return Bool(variable)

    # Check if the value is a string (enclosed in double quotes)
    if value.startswith('"') and value.endswith('"'):
        return String(variable)

    # Check if the value is an integer
    try:
        int(value)
        return Int(variable)
    except ValueError:
        pass

    # Check if the value is a float
    try:
        float(value)
        return Real(variable)
    except ValueError:
        pass

    # If none of the above, return "unknown"
    return "unknown"


def parse_intent_params(graph):
    """
    Scans the given subgraph for nodes whose label matches the pattern:
      <variable> (<returned_type>) = (android.os.Bundle) <object>.<bundle_get_method>("<parameter_name>")
    and returns a dictionary mapping variable names (without any '$') to Z3 variables
    of the appropriate type.
    """
    intent_params = {}
    for node in graph.nodes(data=True):
        label = node[1].get('label', '').strip()
        # Check if the label matches the expected parameter pattern.
        match = PARAM_PATTERN.match(label)
        
        if match:
            var, ret_type, param_name = match.groups()
            var = var.lstrip('$')
            if var not in intent_params:
                # Create a Z3 variable of the appropriate type.
                if "int" in ret_type.lower():
                    intent_params[var] = Int(var)
                elif "string" in ret_type.lower():
                    intent_params[var] = String(var)
                elif "bool" in ret_type.lower():
                    intent_params[var] = Bool(var)
                elif "float" in ret_type.lower():
                    intent_params[var] = Real(var)
                else: # If the type is not recognized, default to an integer.
                    intent_params[var] = Int(var)
    
    print("intent_parameters: ", intent_params)
    return intent_params
            

def parse_if(graph):

    if_parameters = {}
    conditions = []
    for node in graph.nodes(data=True):
        node_id = node[0]
        label = node[1].get('label', '').strip()

        if label.startswith('if '):
            # Extract the condition part of the label (between "if " and "goto").
            match = re.search(r'if\s+(.+?)\s+goto', label)
            if match:
                condition = match.group(1).lstrip('$')
                cond_param = condition.split('==')[0]
                cond_value = condition.split('==')[1]
                if cond_param not in if_parameters:
                    if_parameters.update({cond_param: infer_type(cond_param, cond_value)})
                    #if_parameters.append(cond_param)

                for successor in graph.successors(node_id):
                    edge_data = graph.get_edge_data(node_id, successor)
                    edge_label = edge_data.get('label', '') if edge_data else ''
                    #print(edge_label, condition)
                    if edge_label == 'false':
                        neg_condition = f"Not({condition})"
                        #print(neg_condition)
                        if neg_condition not in conditions:
                            conditions.append(neg_condition)
                        #print("false case: ", conditions)
                    elif edge_label == 'true':
                        #print(condition)
                        if condition not in conditions:
                            conditions.append(condition)
                        #print("true case: ", conditions)

    print("if_parameters: ", if_parameters)
    print("conditions: ", conditions)
    return if_parameters, conditions
            

# Load the DOT file and extract subgraphs (paths).
subgraphs = parse_dot_file("paths.dot")

for i in range(1, len(subgraphs)):
    pathName = f"path_{i}"
    print(pathName)
    intent_params = parse_intent_params(subgraphs[pathName])
    if_parameters, conditions = parse_if(subgraphs[pathName])
    parameters = if_parameters | intent_params

    solver = Solver()

    for condition in conditions:
        solver.add(eval(condition, {"Not": Not}, parameters))

    if solver.check() == sat:
        model = solver.model()
        print(f"n1 = {model[parameters.get('i0')] if model[parameters.get('i0')] is not None else 'No restriction on this value'}")
        print(f"n2 = {model[parameters.get('i1')] if model[parameters.get('i1')] is not None else 'No restriction on this value'}")
        print(f"Op = {model[parameters.get('r2')] if model[parameters.get('r2')] is not None else 'No restriction on this value'}")
        print(f"b4 = {model[parameters.get('b4')] if model[parameters.get('b4')] is not None else 'No restriction on this value'}")
    else:
        print("No solution found")

    print("-"*50)
    
    
    