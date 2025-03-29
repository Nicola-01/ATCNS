import re
import os
import networkx as nx
import pygraphviz as pgv
from z3 import Int, String, Bool, Real, Solver, sat, Not, StringVal, DeclareSort, Const, Length

# Regex for getAction()
GETACTION_PATTERN = re.compile(
    r'^\$?(\w+)\s*\(([^)]+)\)\s*=\s*\(android\.content\.Intent\)\s*\$?\w+\.getAction\(\)'
)

# Regex pattern for matching the parameter definition in the node label.
INTENT_PARAM_PATTERN = re.compile(
    r'^\$?(\w+)\s*\(([^)]+)\)\s*=\s*\(android\.(?:os\.Bundle|content\.Intent)\)\s*\$?\w+\.get\w+\("([^"]+)"(?:,\s*[^)]+)?\)'
)

# Regex pattern to capture iterator hasNext() invocations.
ITERATOR_PATTERN = re.compile(
    r'^\$?(\w+)\s*=\s*interfaceinvoke\s+\$?\w+\.<java\.util\.Iterator:\s*boolean\s+hasNext\(\)>\(\)'
)

STANDARD_VAR_DECLARATION_PATTERN = re.compile(
    r'^(?P<variable>\$?\w[\w\d_]*)\s+\((?P<return_type>[^)]+)\)\s*=\s*\((?P<package>[^)]+)\)\s+'
    r'(?P<object>\$?\w[\w\d_]*)\.(?P<method>\w+)\((?P<parameter>[^)]*)\)$'
)

PACKAGE_VAR_DECLARATION_PATTERN = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*\(\s*(?P<return_type>[^)]+)\s*\)\s*='
    r'\s*\(\s*(?P<package>[^)]+)\s*\)\.(?P<method>\w+)\(\s*(?P<parameters>.*?)\s*\)$'
)

SIMPLE_VAR_ASSIGNATION = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*='
    r'\s*\(\s*(?P<type>[^)]+)\s*\)\s*(?P<object_name>[\w\$\.]+)$'
)

RETURN_VAR_ASSIGNATION_PATTERN = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*\(\s*(?P<type>[^)]+)\s*\)\s*=\s*'
    r'\(return\.(?P<return_var>[\w\$]+)\)\s*(?P<value>.+)$'
)

THIS_VAR_ASSIGNATION_PATTERN = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*'
    r'\(\s*(?P<type>[^)]+)\s*\)\s*'
    r'=\s*r0_this_(?P<param>[\w\$]+)(?:_(?P<number>\d+))?$'
)

THIS_VAR_DECLARATION_PATTERN = re.compile(
    r'^r0_this_(?P<var_name>[\w\$]+)\s*'  # Matches "r0_this_" and then the variable name
    r'\(\s*(?P<type>[^)]+)\s*\)\s*'         # Matches the type in parentheses, allowing for spaces
    r'=\s*(?P<value>.+)$'                  # Matches "=" followed by the object or value
)

OPERATION_VAR_ASSIGNATION_PATTERN = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*=\s*'  
    r'(?P<operand1>[\w\$]+)\s*(?P<operation>[\+\-\*/%])\s*(?P<operand2>[\w\$]+)\s*$'       
)

OPERATOR_PATTERN = re.compile(
    r'(\w+)\s*(==|<=|>=|<|>|!=)\s*(.+)'
)

LENGTH_PATTERN = re.compile(
    r'^\$?(\w+)\s*\(([^)]+)\)\s*=\s*\(java\.lang\.String\)\s*\$?(\w+)\.length\(\)'
)

# Operators that have to be inverted if necessary
INVERT_OPERATOR = ["<=", ">=", "<", ">"]
# Inverted operator map
INVERT_OP_MAP = {
    "<=": ">=",
    ">=": "<=",
    "<": ">",
    ">": "<"
}

# Mapping for displaying types in the desired format.
TYPE_MAPPING = {
    "Int": "integer",
    "String": "string",
    "Bool": "boolean",
    "Real": "float",
    "Char": "char",
    "Serializable": "serializable"
}

# Declare a new uninterpreted sort for serializable types.
SerializableSort = DeclareSort('Serializable')
NULL_SERIALIZABLE = Const("null_serializable", SerializableSort)

# Define a Z3 constant representing null for strings.
NULL = StringVal("null")

Z3_CONTEST = {
    "Length": Length, 
    "Not": Not, 
    "null": NULL, 
    "null_serializable": NULL_SERIALIZABLE
}

intent_params, param_name_map, if_parameters, custom_types = {}, {}, {}, {}
conditions = []


def reset_globals():
    """Reset global variables for each new subgraph."""
    global intent_params, param_name_map, if_parameters, custom_types, conditions
    intent_params = {}
    param_name_map = {}
    if_parameters = {}
    custom_types = {}
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
    metadata = {}
    with open(dot_path, "r", encoding="utf-8") as file:
        for line in file:
            if line.startswith("#"):
                match = re.match(r"#\s*(apkFile|sdkVersion|package|activity|action):\s*(.+)", line)
                if match:
                    key, value = match.groups()
                    metadata[key] = value.strip()
            else:
                return metadata
    return metadata

def infer_type(variable, value):
    # If value is in the form "(...)" we try to infer from the text inside.
    if value.startswith("(") and value.endswith(")"):
        if "string" in value.lower():
            return String(variable)
        elif "int" in value.lower():
            return Int(variable)
        elif "boolean" in value.lower():
            return Bool(variable)
        elif "serializable" in value.lower():
            return Const(variable, SerializableSort)
        else:
            # For any non-default type, use the full text inside the parentheses as the type name.
            type_name = value[1:-1].strip().replace(".", "_")
            if type_name not in custom_types:
                # Create a new custom Z3 sort with a unique name.
                custom_sort = DeclareSort(f"{type_name}")
                null_custom = Const(f"null_{type_name}", custom_sort)
                custom_types[type_name] = (custom_sort, null_custom)
                if null_custom not in Z3_CONTEST:
                    Z3_CONTEST[f"null_{type_name}"] = null_custom
            else:
                custom_sort, null_custom = custom_types[type_name]
                if null_custom not in Z3_CONTEST:
                    Z3_CONTEST[f"null_{type_name}"] = null_custom
            return Const(variable, custom_sort)
          
    else:
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
    definitions (including iterator nodes and getAction()), but only processes nodes with color=blue.
    """
    global intent_params, param_name_map
    for node, data in get_blue_nodes(graph):
        label = data.get('label', '').strip()
        # First, check for getAction() nodes.
        match = GETACTION_PATTERN.match(label)
        if match:
            var, ret_type = match.groups()
            var = var.lstrip('$')
            if var not in param_name_map:
                # Here we set the parameter name to "action" (or any identifier you choose).
                param_name_map[var] = "action"
            if var not in intent_params:
                # Typically, getAction() returns a String, so we create a String variable.
                if "string" in ret_type.lower():
                    intent_params[var] = String(var)
            continue  # Skip further processing for this node.

        # try matching the normal parameter pattern.
        match = INTENT_PARAM_PATTERN.match(label)
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
                elif "serializable" in ret_type.lower():
                    # Instead of ignoring, create a custom type for serializable.
                    intent_params[var] = Const(var, SerializableSort)
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
    
    for node_id, data in blue_nodes:
        label = data.get('label', '').strip()
        if label.startswith('if '):
            # Extract condition (the part between "if" and "goto")
            match = re.search(r'if\s+(.+?)\s+goto', label)
            if match:
                match_condition = match.group(1).lstrip('$')
                op_match = OPERATOR_PATTERN.match(match_condition)

                if op_match:
                    cond_param, operator, cond_value = op_match.groups()
                    cond_param = cond_param.strip().lstrip("$")
                    cond_value = cond_value.strip().lstrip("$")
                    length_condition = False
                    #print("cond_param: ", cond_param, " cond_value: ", cond_value)
                    # If the variable is not yet known, try to infer its type.
                    if cond_param not in if_parameters and cond_param not in intent_params:    
                        var_condition, length_condition = search_for_var_declaration(graph, cond_param, operator, cond_value)
                        #if cond_param=="r4_4":
                        #    print("var_condition: ", var_condition)
                        if var_condition and var_condition not in conditions:
                            #print("var_condition: ", var_condition)
                            conditions.append(var_condition)
                        # If still cond_param isn't in the dictionaries after the search_var_declaration() function call, insert it    
                        if cond_param not in if_parameters and cond_param not in intent_params:
                            if_parameters[cond_param] = infer_type(cond_param, cond_value)
                
                    # Case where the if involves 2 variables: i0 == r3
                    # If cond_value is not a numeric literal, a quoted string or null, assume it's a variable.
                    if (not cond_value.isdigit() and
                        not (cond_value.startswith('"') and cond_value.endswith('"')) and 
                        not cond_value=="null"):
                        #print("cond_value: ", cond_value)
                        # If not already in parameters, search for its declaration.
                        if cond_value not in intent_params and cond_value not in if_parameters:
                            decl, length_condition = search_for_var_declaration(graph, var_name=cond_value, operator=operator, cond_value=cond_param, invert_op=True)
                            #if length_condtion:
                                #print(decl, " ", length_condtion)
                            if decl:
                                # Assume decl is in the form "var==value" so we extract the value part.
                                try:
                                    _, decl_value = decl.split("==")
                                    decl_value = decl_value.strip()
                                except Exception:
                                    decl_value = "0"  # fallback
                                # Add the variable using inferred type (or default to Int here)
                                if cond_value not in if_parameters:
                                    if_parameters[cond_value] = infer_type(cond_value, decl_value)
                    
                    # If the variable is serializable, replace "null" with "null_serializable"
                    if (cond_param in intent_params and intent_params[cond_param].sort().name() == "Serializable") or \
                       (cond_param in if_parameters and if_parameters[cond_param].sort().name() == "Serializable"):
                        if cond_value=="null":
                            cond_value = cond_value.replace("null", "null_serializable")

                    # If the variable is a custom object, replace "null" with custom null
                    if (cond_param in intent_params and intent_params[cond_param].sort().name() in custom_types) or \
                       (cond_param in if_parameters and if_parameters[cond_param].sort().name() in custom_types):
                        if cond_value.strip() == "null":
                            #print("cond_param: ", cond_param, "value: ", cond_value)
                            if cond_param in intent_params:
                                custom_name = intent_params[cond_param].sort().name()
                            #    print(custom_name)
                            elif cond_param in if_parameters:
                                custom_name = if_parameters[cond_param].sort().name()
                            #    print(custom_name)
                            cond_value = f"null_{custom_name}"
                            #print(cond_value)

                    if not length_condition:
                        # Reconstruct the condition string with the (possibly transformed) cond_value.
                        condition = f"{cond_param} {operator} {cond_value}"
                        #print("final condition: ", condition)
                    #print("initial condition: ", condition)
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

                    #print(cond_param, " ", operator, " ", cond_value)
                    # If the variable is a boolean (for instance, an iterator's hasNext)
                    # then convert "0" to "False" and "1" to "True".
                    if (cond_param in intent_params and intent_params[cond_param].sort().name() == "Bool") or \
                       (cond_param in if_parameters and if_parameters[cond_param].sort().name() == "Bool"):
                        #print(cond_param)
                        pattern = rf'(Not\s*\(\s*)?\b{cond_param}\s*==\s*(0|1)'
    
                        for idx, cond in enumerate(conditions):
                            match = re.search(pattern, cond)
                            if match:
                                cond_value = match.group(2)  # Capture the 0 or 1
                                new_literal = "False" if cond_value == "0" else "True"

                                # Replace the matched 0/1 with False/True, keeping other parts intact
                                new_cond = re.sub(pattern, rf'\1{cond_param} == {new_literal}', cond)
                                conditions[idx] = new_cond
                                #cond_value = new_literal
                                break  # Stop after the first matching condition
                           
                            
def search_for_var_declaration(graph, var_name, operator="", cond_value="", invert_op=False):
    """
    Searches (only in blue nodes) for a declaration of a variable, and returns a condition
    string if found.
    """
    def find_var(nodes):
        var_condition = ""
        length_condition = False
        for node, data in nodes:
            label = data.get('label', '').strip()

            pattern = re.compile(r'^(?P<var_name>[\w\$.]+)\s*\(\s*(?P<type>[^)]+)\s*\)$')
            if pattern.match(label.split(' = ')[0].strip().lstrip("$")):
                final_condition = var_name==label.split(' = ')[0].strip().lstrip("$").split(" ")[0]
            else:
                final_condition = var_name==label.split(' = ')[0].strip().lstrip("$")

            if not label.startswith("if") and ' = ' in label and len(label.split(' = ')) == 2 and final_condition:
                variable, value = label.split(" = ", 1)
                variable = variable.replace("$", "")
                value = value.replace("$", "") if value.startswith("$") else value
                #if var_name=="r11_2":
                #    print(var_name, " ",variable, ", ", value)
                    
                if LENGTH_PATTERN.match(label) and var_name==LENGTH_PATTERN.match(label).group(1):
                    obj_name = LENGTH_PATTERN.match(label).group(3)
                    if obj_name=="r11_2":
                        print(obj_name, " ", operator, " ", cond_value)
                    # Ensure the object is in our parameters dictionary
                    if obj_name not in intent_params and obj_name not in if_parameters:
                        # Infer its type from elsewhere or default to String
                        if_parameters[obj_name] = String(obj_name)
                    
                    if invert_op and operator in INVERT_OPERATOR:
                        len_cond = f"Length({obj_name}) {INVERT_OP_MAP[operator]} {cond_value}"
                    else:
                        len_cond = f"Length({obj_name}) {operator} {cond_value}"

                    if not len_cond in conditions:
                        conditions.append(len_cond)
                    length_condition = True
                    break

                elif STANDARD_VAR_DECLARATION_PATTERN.match(label) or \
                    PACKAGE_VAR_DECLARATION_PATTERN.match(label) or \
                    RETURN_VAR_ASSIGNATION_PATTERN.match(label) or \
                    THIS_VAR_ASSIGNATION_PATTERN.match(label) or \
                    THIS_VAR_DECLARATION_PATTERN.match(label):
                    #if var_name=="r11_2":
                    #    print(variable, " : ", value)
                    var, type = variable.split(" ", 1)
                                 
                    if var not in if_parameters and var not in intent_params:
                        if_parameters[var] = infer_type(var, type)

                    if RETURN_VAR_ASSIGNATION_PATTERN.match(label):
                        return_match = RETURN_VAR_ASSIGNATION_PATTERN.match(label)
                        if return_match.group("value") != "null":
                            add_new_condition(graph, return_match.group("value").lstrip("$"))
                        var_condition = f"{var} == {return_match.group("value")}"
                    elif THIS_VAR_ASSIGNATION_PATTERN.match(label):
                        #print("value: ", value)
                        add_new_condition(graph, value)
                        var_condition = f"{var} == {value}"
                            #if var=="r11_3":
                            #    print(var_condition)
                    elif THIS_VAR_DECLARATION_PATTERN.match(label):
                        #print("value: ", value)
                        add_new_condition(graph, value)
                        var_condition = f"{var} == {value}"
                            #if var=="r0_this.mQuery_1":
                            #    print(var_condition)

                elif OPERATION_VAR_ASSIGNATION_PATTERN.match(label):
                    operation_match = OPERATION_VAR_ASSIGNATION_PATTERN.match(label)
                    var = operation_match.group("var_name")
                    operand1 = operation_match.group("operand1")
                    operand2 = operation_match.group("operand2")
                    operation = operation_match.group("operation")

                    if var not in if_parameters or var not in intent_params:
                        if_parameters[var] = Int(var)
                    if not operand1.isdigit() and (operand1 not in if_parameters or operand1 not in intent_params):
                        if_parameters[operand1] = Int(operand1)
                    if not operand2.isdigit() and (operand2 not in if_parameters or operand2 not in intent_params):
                        if_parameters[operand2] = Int(operand2)

                    var_condition = f"{var} == {operand1} {operation} {operand2}"

                elif '==' in value:
                    # In case there's "==" in the value of the variable, the variable is Bool
                    if var_name not in if_parameters and var_name not in intent_params:
                        if_parameters[var_name] = Bool(var_name)
                    var1, var2 = value.split("==")
                    var1 = var1.strip()
                    var2 = var2.strip()
                    #print("vars: ", var1, var2)
                    if var1 not in intent_params and var1 not in if_parameters:
                        add_new_condition(graph, var1)
                    if var2 not in intent_params and var2 not in if_parameters:
                        add_new_condition(graph, var2)
                    var_condition = f"{variable} == ({var1}=={var2})"

                elif not (' ' in variable):
                    #print("var: ", variable, " value: ", value)
                    if SIMPLE_VAR_ASSIGNATION.match(label):
                        type = "(" + SIMPLE_VAR_ASSIGNATION.match(label).group("type") + ")"
                        object_name = SIMPLE_VAR_ASSIGNATION.match(label).group("object_name").lstrip("$")
                        #add_new_condition(graph, object_name)
                        var_condition = f"{variable} == {object_name}"
                        if variable not in if_parameters and variable not in intent_params:
                            if_parameters[variable] = infer_type(variable, type)
                        if object_name not in if_parameters and object_name not in intent_params:
                            if_parameters[object_name] = infer_type(object_name, type)
                    else:
                        var_condition = f"{variable} == {value}"
                        if variable not in if_parameters and variable not in intent_params:
                            if_parameters[variable] = infer_type(variable, value)

                # Check for iterator hasNext() invocations.
                iterator_match = ITERATOR_PATTERN.match(label)
                if iterator_match:
                    var = iterator_match.group(1).lstrip('$')
                    if var not in if_parameters and var not in intent_params:
                        if_parameters[var] = Bool(var)
                        return
        #if var_condition:
        #    print(var_condition)    
        return var_condition, length_condition
    
    var, len_cond = find_var(get_blue_nodes(graph))

    if not var and (var_name not in intent_params and var_name not in if_parameters):
        var, len_cond = find_var(graph.nodes(data=True))

    return var, len_cond

def add_new_condition(graph, var_name):
    """
    Attempts to add a new condition for a variable declaration (only searching blue nodes).
    """
    global intent_params, if_parameters, conditions
    var_condition, _ = search_for_var_declaration(graph, var_name)
    #print("var_condition: ", var_condition)
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
        output_file.write(f"{key}: {value}\n")
    output_file.write("\n")
    
    # Process each subgraph (path).
    for i in range(1, len(subgraphs) + 1):
        pathName = f"path_{i}"
        parse_intent_params(subgraphs[pathName])
        parse_if(subgraphs[pathName])
        parameters = if_parameters | intent_params
        print(pathName)
        #print("custom_types: ", custom_types)
        #print("z3_contest: ", Z3_CONTEST)
        print("Conditions: ", conditions)
        print("Parameters: ", parameters)
        print("Intent Parameters: ", intent_params)
        solver = Solver() 
        # When evaluating conditions, we include our special null constants.
        for condition in conditions: 
            solver.add(eval(condition, Z3_CONTEST, parameters))
        
        solution_line = ""
        if solver.check() == sat:
            #print(pathName)
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
                # If the variable is serializable, also include conditions involving it.
                if sort_name == "Serializable":
                    serializable_conditions = [cond for cond in conditions if param in cond]
                    if serializable_conditions:
                        value_str += " | Conditions: " + ", ".join(serializable_conditions)
                param_strings.append(f"{param_name_map.get(param, param)} ({type_str}) : {value_str}")
            solution_line = " | ".join(param_strings)
            output_file.write(f"{solution_line}\n")
            print(solution_line)
        else:
            print("No solution")
        print("-"*50)
        reset_globals()
        
print("\nAnalysis complete. Results written to 'analysis_results.txt'.")
