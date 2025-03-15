import re
import os
import networkx as nx
import pygraphviz as pgv
from z3 import Int, String, Bool, Real, Solver, sat, Not, StringVal

# Regular expression pattern for matching the parameter definition in the node label.
PARAM_PATTERN = re.compile(
    r'^\$?(\w+)\s*\(([^)]+)\)\s*=\s*\(android\.(?:os\.Bundle|content\.Intent)\)\s*\$?\w+\.get\w+\("([^"]+)"(?:,\s*[^)]+)?\)'
)

# Mapping for displaying types in the desired format.
TYPE_MAPPING = {
    "Int": "integer",
    "String": "string",
    "Bool": "boolean",
    "Real": "float",
    "Char": "char"
}

intent_params, param_name_map, if_parameters = {}, {}, {}
conditions = []

# Define a Z3 constant representing null for strings.
NULL = StringVal("null")

def reset_globals():
    """Reset global variables for each new subgraph."""
    global intent_params, param_name_map, if_parameters, conditions
    intent_params = {}
    param_name_map = {}
    if_parameters = {}
    conditions = []

def parse_dot_file(dot_path):
    """
    Reads the DOT file and returns a dictionary of NetworkX directed graphs.
    Each subgraph in the DOT file is assumed to represent a distinct path.
    """
    A = pgv.AGraph(dot_path)
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
            else:
                return metadata
    return metadata

def infer_type(variable, value):
    if value.lower() == "true" or value.lower() == "false":
        return Bool(variable)
    if value.startswith('"') and value.endswith('"'):
        return String(variable)
    try:
        int(value)
        return Int(variable)
    except ValueError:
        pass
    try:
        float(value)
        return Real(variable)
    except ValueError:
        pass
    return "unknown"

# --- New Helper Function ---
def get_blue_nodes(graph):
    """
    Returns a list of tuples (node_id, node_data) for nodes with attribute color=blue,
    in topological order.
    """
    return [(node, graph.nodes[node])
            for node in nx.topological_sort(graph)
            if graph.nodes[node].get('color') == 'blue']

def parse_intent_params(graph):
    """
    Scans the given subgraph for nodes whose label matches the pattern for parameter
    definitions, but only processes nodes with color=blue.
    """
    global intent_params, param_name_map
    for node, data in get_blue_nodes(graph):
        label = data.get('label', '').strip()
        match = PARAM_PATTERN.match(label)
        if match:
            var, ret_type, param_name = match.groups()
            var = var.lstrip('$')
            if var not in param_name_map:
                param_name_map[var] = param_name
            if var not in intent_params:
                if "int" in ret_type.lower():
                    intent_params[var] = Int(var)
                elif "string" in ret_type.lower():
                    intent_params[var] = String(var)
                elif "bool" in ret_type.lower():
                    intent_params[var] = Bool(var)
                elif "float" in ret_type.lower():
                    intent_params[var] = Real(var)
                elif "Serializable" in ret_type.lower():
                    continue
                else:
                    intent_params[var] = Int(var)

def parse_if(graph):
    """
    Scans the subgraph (only blue nodes) for 'if' nodes, processes the conditions, and
    then uses only the blue successors (i.e. the next blue node) to determine whether to
    add the condition or its negation.
    """
    global if_parameters, conditions
    blue_nodes = get_blue_nodes(graph)
    blue_node_ids = {node for node, _ in blue_nodes}
    
    for node_id, data in blue_nodes:
        label = data.get('label', '').strip()
        if label.startswith('if '):
            # Extract condition (the part between "if" and "goto")
            match = re.search(r'if\s+(.+?)\s+goto', label)
            if match:
                condition = match.group(1).lstrip('$')
                op_regex = re.compile(r'(\w+)\s*(==|<=|>=|<|>|!=)\s*(.+)')
                op_match = op_regex.match(condition)
                if op_match:
                    cond_param, operator, cond_value = op_match.groups()
                    cond_param = cond_param.strip()
                    cond_value = cond_value.strip()
                    if cond_param not in if_parameters and cond_param not in intent_params:
                        if_parameters[cond_param] = infer_type(cond_param, cond_value)
                        var_condition = search_for_var_declaration(graph, cond_param)
                        if var_condition:
                            conditions.append(var_condition)
                    
                    # Instead of all successors, consider only those successors that are blue.
                    blue_successors = [s for s in graph.successors(node_id)
                                       if graph.nodes[s].get('color') == 'blue']
                    if blue_successors:
                        for successor in blue_successors:
                            edge_data = graph.get_edge_data(node_id, successor)
                            edge_label = edge_data.get('label', '') if edge_data else ''
                            if edge_label == 'false':
                                neg_condition = f"Not({condition})"
                                if neg_condition not in conditions:
                                    conditions.append(neg_condition)
                            elif edge_label == 'true':
                                if condition not in conditions:
                                    conditions.append(condition)
                    else:
                        if condition not in conditions:
                            conditions.append(condition)

def search_for_var_declaration(graph, var_name):
    """
    Searches (only in blue nodes) for a declaration of a variable, and returns a condition
    string if found.
    """
    var_condition = ""
    for node, data in get_blue_nodes(graph):
        label = data.get('label', '').strip()
        if ' = ' in label and len(label.split(' = ')) == 2 and var_name in label.split(' = ')[0]:
            variable, value = label.split(" = ", 1)
            variable = variable.replace("$", "")
            value = value.replace("$", "") if value.startswith("$") else value
            if ' ' in variable:
                continue
            if not (' ' in variable):
                var_condition = f"{variable}=={value}"
            if '==' in value:
                var1, var2 = value.split("==")
                var1 = var1.strip()
                var2 = var2.strip()
                if var1 not in intent_params and var1 not in if_parameters:
                    add_new_condition(graph, var1)
                if var2 not in intent_params and var2 not in if_parameters:
                    add_new_condition(graph, var2)
                var_condition = f"{variable}==({var1}=={var2})"
    return var_condition

def add_new_condition(graph, var_name):
    """
    Attempts to add a new condition for a variable declaration (only searching blue nodes).
    """
    global intent_params, if_parameters, conditions
    var_condition = search_for_var_declaration(graph, var_name)
    if var_condition:
        variable, value = var_condition.split("==")
        if variable not in if_parameters and variable not in intent_params:
            if_parameters[variable] = infer_type(variable, value)
        if var_condition not in conditions:
            conditions.append(var_condition)

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
subgraphs = parse_dot_file(dot_file)
metadata = extract_metadata(dot_file)

with open("analysis_results.txt", "w", encoding="utf-8") as output_file:
    for key, value in metadata.items():
        output_file.write(f"{key.capitalize()}: {value}\n")
    output_file.write("\n")
    
    # Process each subgraph (path).
    for i in range(1, len(subgraphs) + 1):
        pathName = f"path_{i}"
        parse_intent_params(subgraphs[pathName])
        parse_if(subgraphs[pathName])
        parameters = if_parameters | intent_params
        print("Conditions: ", conditions)
        print("Parameters: ", parameters)
        solver = Solver() 
        for condition in conditions: 
            solver.add(eval(condition, {"Not": Not, "null": NULL}, parameters))
        
        solution_line = ""
        if solver.check() == sat:
            model = solver.model()
            param_strings = []
            for param, z3_var in sorted(intent_params.items()):
                sort_name = z3_var.sort().name()
                type_str = TYPE_MAPPING.get(sort_name, sort_name)
                value = model[z3_var]
                if value is None:
                    value_str = "[no lim]"
                else:
                    if sort_name == "String":
                        value_str = f'{value}'
                    else:
                        value_str = str(value)
                param_strings.append(f"{param_name_map.get(param)} ({type_str}) : {value_str}")
            solution_line = " | ".join(param_strings)
            output_file.write(f"{solution_line}\n")
            print(f"{pathName}\n", solution_line)
        print("-"*50)
        reset_globals()
        
print("Analysis complete. Results written to 'analysis_results.txt'.")
