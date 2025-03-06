import re
import os
import networkx as nx
import pygraphviz as pgv
from z3 import Int, String, Bool, Real, Solver, sat, Not

# Regular expression pattern for matching the parameter definition in the node label.
PARAM_PATTERN = re.compile(
    r'^\$?(\w+)\s*\(([^)]+)\)\s*=\s*\(android\.(?:os\.Bundle|content\.Intent)\)\s*\$?\w+\.get\w+\("([^"]+)"\)'
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

def extract_metadata(dot_path):
    """
    Extracts package, activity, and action from the DOT file header.
    """
    metadata = {"package": None, "activity": None, "action": None}

    with open(dot_path, "r", encoding="utf-8") as file:
        for line in file:
            if line.startswith("#"):
                match = re.match(r"#\s*(package|activity|action):\s*(.+)", line)
                if match:
                    key, value = match.groups()
                    metadata[key] = value.strip()
            else: return metadata

    return metadata


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
                # Regex for capturing a variable, an operator, and a value.
                op_regex = re.compile(r'(\w+)\s*(==|<=|>=|<|>|!=)\s*(.+)')
                op_match = op_regex.match(condition)
                if op_match:
                    cond_param, operator, cond_value = op_match.groups()
                    cond_param = cond_param.strip()
                    cond_value = cond_value.strip()
                
                    if cond_param not in if_parameters:
                        if_parameters.update({cond_param: infer_type(cond_param, cond_value)})
                        var_condition = search_for_var_declaration(graph, cond_param)
                        #print(f"{cond_param} ... {cond_value} ...", var_condition)
                        if var_condition:
                            conditions.append(var_condition)
                        

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

    return if_parameters, conditions
            

def search_for_var_declaration(graph, var_name):
    var_condition = ""
    for node in graph.nodes(data=True):
        label = node[1].get('label', '').strip()

        if (' = ') in label and len(label.split(' = ')) == 2 and var_name in label.split(' = ')[0]:
            variable, value = label.split(" = ", 1)
            variable = variable.replace("$","")
            value = value.replace("$", "") if value.startswith("$") else value
            if (' ') in variable: continue
            if not((' ') in variable):
                var_condition = f"{variable}=={value}"
            if ('==') in value: 
                var_condition = f"{variable}==({value})"
            

    return var_condition


# ---------------------------
# MENU: Select a DOT file to analyze
# ---------------------------
paths_dir = "paths"
files = [f for f in os.listdir(paths_dir) if f.endswith('.dot') and os.path.isfile(os.path.join(paths_dir, f))]

if not files:
    print("No .dot files found in the 'paths' directory.")
    exit()

def display_menu():
    print("Select a file to analyze:\n")
    for idx, file in enumerate(files, start=1):
        print(f"{idx}. {file}")

selected_index = None
while selected_index is None:
    display_menu()
    try:
        selection = int(input("\nEnter the number of the file: "))
        if 1 <= selection <= len(files):
            selected_index = selection - 1
        else:
            print("Invalid selection. Please try again.\n")
    except ValueError:
        print("Invalid input. Please enter a valid number.\n")

dot_file = os.path.join(paths_dir, files[selected_index])
print(f"Selected file: {dot_file}\n")


# Load the DOT file and extract subgraphs (paths).
#dot_file = "paths/com.example.complexcalculator.Calculator.onCreate_paths.dot"
# dot_file = "paths/paths.dot"
subgraphs = parse_dot_file(dot_file)
metadata = extract_metadata(dot_file)
# print(metadata)

for i in range(1, len(subgraphs)+1):
    pathName = f"path_{i}"
    # print(pathName)
    
    intent_params = parse_intent_params(subgraphs[pathName])
    # print("intent_parameters: ", intent_params)
    
    if_parameters, conditions = parse_if(subgraphs[pathName])
    # print("if_parameters: ", if_parameters)
    # print("conditions: ", conditions)
    
    parameters = if_parameters | intent_params 
    #print("Parameters", parameters)
    solver = Solver()

    for condition in conditions:
        solver.add(eval(condition, {"Not": Not}, parameters))


    if solver.check() == sat:
        print(pathName)
        model = solver.model()
        
        for param_name, z3_var in intent_params.items():
            value = model[z3_var]
            print(f"{param_name} = {value if value is not None else 'No restriction on this value'}")

        print("-"*50)
    # else:
    #     print("No solution found")

    # print("-"*50)  

# print(metadata)