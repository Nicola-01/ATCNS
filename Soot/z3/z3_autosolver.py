import re
import os
import networkx as nx
import pygraphviz as pgv
from z3 import Int, String, Bool, Real, Array, Solver, sat, Not, StringVal, DeclareSort, IntSort, StringSort, BoolSort, RealSort, SortRef, Const, Length, Implies, Or, And

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

ARRAY_DECLARATION_PATTER = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*=\s*newarray\s*\(\s*(?P<type>[\w\.\$]+)\s*\)\s*\[\s*(?P<length>\d+)\s*\]$'
)

ARRAY_ELEMENT_ASSIGNATIO_PATTERN = re.compile(
    r'^(?P<array_name>[\w\$]+)\[\s*(?P<index>\d+)\s*\]\s*=\s*(?P<element_name>[\w\$]+)$'
)

LENGTHOF_PATTERN = re.compile(
    r'^(?P<var_name>[\w\$]+)\s*=\s*lengthof\s+(?P<obj_name>[\w\$]+)$'
)

INSTACEOF_PATTERN = re.compile(
    r'^(?P<variable>[\w\$]+)\s*=\s*(?P<object_name>[\w\$]+)\s+instanceof\s+(?P<full_class_name>[a-zA-Z_][\w\.$]*)$'
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

intent_params, param_name_map, if_parameters, array_params, custom_types = {}, {}, {}, {}, {}
conditions = []


def reset_globals():
    """Reset global variables for each new subgraph."""
    global intent_params, param_name_map, if_parameters, array_params, custom_types, Z3_CONTEST, conditions
    intent_params = {}
    param_name_map = {}
    if_parameters = {}
    array_params = {}
    custom_types = {}
    Z3_CONTEST = {
        "Length": Length, 
        "Not": Not, 
        "null": NULL, 
        "null_serializable": NULL_SERIALIZABLE
    }
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

def create_z3_custom_object(name):
    name = name.replace("$", "")
    if name not in custom_types:
        # Create a new custom Z3 sort with a unique name.
        custom_sort = DeclareSort(f"{name}")
        null_custom = Const(f"null_{name}", custom_sort)
        custom_types[name] = (custom_sort, null_custom)
        if null_custom not in Z3_CONTEST:
            Z3_CONTEST[f"null_{name}"] = null_custom
    else:
        custom_sort, null_custom = custom_types[name]
        if null_custom not in Z3_CONTEST:
            Z3_CONTEST[f"null_{name}"] = null_custom
    return custom_sort

def infer_type(variable, value):
    # If value is in the form "(...)" we try to infer from the text inside.
    if isinstance(value, str):
        if value.startswith("(") and value.endswith(")"):
            if "java.lang.string" in value.lower():
              return String(variable)
            elif "int" == value[1:-1].lower():
              return Int(variable)
            elif "boolean" in value.lower():
              return Bool(variable)
            elif "java.io.serializable" in value.lower():
              return Const(variable, SerializableSort)
            else:
              return Const(variable, create_z3_custom_object(value[1:-1].strip().replace(".", "_")))
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

    elif isinstance(value, SortRef):
        if value == IntSort():
            return Int(variable)
        elif value == BoolSort():
            return Bool(variable)
        elif value == RealSort():
            return Real(variable)
        elif value == StringSort():
            return String(variable)
        else:
            # Check if the sort is one of the custom sorts created with create_z3_custom_object
            for key, (custom_sort, _) in custom_types.items():
                if custom_sort == value:
                    return Const(variable, custom_sort)

def get_blue_nodes(graph):
    """
    Returns a list of tuples (node_id, node_data) for nodes with attribute color=blue,
    in topological order.
    """
    return [(node, graph.nodes[node])
            for node in nx.topological_sort(graph)
            if graph.nodes[node].get('color') == 'blue']
"""
def create_array_implies_conditions(array_name, array_length, array_length_key):
    ""
    Given the name of the array, its logical length, and a dictionary where the array is stored,
    this function:
      - retrieves the array,
      - inspects its elements to determine the element type,
      - determines the proper "null" value for that type (using global definitions for String, Serializable,
        and custom types),
      - builds two conditions:
          1. For every index i in the allocated array, if i < array_length then array[i] != null_value.
          2. For every index i, if i >= array_length then array[i] == null_value.
    These conditions are appended to the global `conditions` list and returned.
    ""
    # Retrieve the array from the dictionary.
    array = array_params.get(array_name)
    
    # Try to infer the type from the first non-null element.
    sample = None
    for elem in array:
        if elem is not None:
            sample = elem
            break

    if sample is None:
        # If the array is empty (or all None), default to Int.
        sample_sort = IntSort()
        null_value = 0
    else:
        sample_sort = sample.sort()
        # Determine the null value based on the sort.
        if sample_sort == StringSort():
            null_value = NULL  # Defined as StringVal("null")
        elif sample_sort == SerializableSort:
            null_value = NULL_SERIALIZABLE
        elif sample_sort == IntSort():
            null_value = 0
        elif sample_sort == RealSort():
            null_value = 0
        elif sample_sort == BoolSort():
            null_value = False
        else:
            # Assume it is a custom type.
            # Use the sort's name to look up the custom null.
            null_key = f"null_{sample_sort.name()}"
            null_value = Z3_CONTEST.get(null_key, None)
            if null_value is None:
                # If not already set up, create one and record it.
                null_value = Const(null_key, sample_sort)
                Z3_CONTEST[null_key] = null_value

    if array_length_key in if_parameters:
        length_param = if_parameters.get(array_length_key, None)
    #for i in range(int(array_length)):
    #    print(i)
    if length_param is not None:
        # If array_length > 0 then there exists an index in [0, array_length) where the element is not null
        nonull_condition = Implies(length_param > 0, Or([array[i] != null_value for i in range(int(array_length))]))
        # If array_length == 0 then for all valid indices the array's element is null.
        null_condition = Implies(length_param == 0, And([array[i] == null_value for i in range(int(array_length))]))
        return nonull_condition, null_condition
"""

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
                # Here we set the parameter name to "action".
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
                    isArray = False
                    #if cond_param=="r4_3":
                    #print("parse_if: cond_param: ", cond_param, " operator: ", operator,  " cond_value: ", cond_value)
                    # If the variable is not yet known, try to infer its type.
                    if cond_param not in if_parameters and cond_param not in intent_params:    
                        var_condition, length_condition = search_for_var_declaration(graph, cond_param, operator, cond_value)
                        #if cond_param=="r4_3":
                        #    print("var_condition: ", var_condition)
                        if var_condition and var_condition not in conditions:
                            #print("var_condition: ", var_condition)
                            conditions.append(var_condition)
                        # If still cond_param isn't in the dictionaries after the search_var_declaration() function call, insert it    
                        if cond_param not in if_parameters and cond_param not in intent_params:
                            if not cond_value == "null":
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
                                else:
                                    # If cond_value is inside the dictionary with value None, get the correct type by the cond_param type. They are compared in an if so they must be the same type object
                                    if if_parameters[cond_value] == None:
                                        if cond_param in intent_params:
                                            sort = intent_params[cond_param].sort()
                                        elif cond_param in if_parameters:
                                            sort = if_parameters[cond_param].sort()
                                        if_parameters[cond_value] = Const(cond_value, sort)

                    #print("cond_param: ", cond_param, " cond_value: ", cond_value, " ", cond_param in intent_params, " ", cond_param in if_parameters)
                    # If the variable is serializable, replace "null" with "null_serializable"
                    if (cond_param in intent_params and intent_params[cond_param].sort().name() == "Serializable") or \
                       (cond_param in if_parameters and if_parameters[cond_param].sort().name() == "Serializable"):
                        if cond_value.strip() == "null":
                            cond_value = cond_value.replace("null", "null_serializable")

                    # If the variable is alist, replace "null" with 0 and check the length of the array
                    if (cond_param in array_params and isinstance(array_params[cond_param], list)):
                        if cond_value.strip() == "null":
                            length_key = f"length_{cond_param}"
                            if length_key in if_parameters:
                                #array_length = len(array_params.get(cond_param))
                                #array_name = cond_param
                                cond_param = length_key
                                cond_value = cond_value.replace("null", "0")
                                #isArray = True

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
                    """
                    if isArray:
                        nonull_array_condition, null_array_condition = create_array_implies_conditions(array_name, array_length, length_key) 
                        if nonull_array_condition not in conditions:
                            conditions.append(nonull_array_condition)
                        if null_array_condition not in conditions:
                            conditions.append(null_array_condition)
                    """
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

                    # If the variable is a boolean (for instance, an iterator's hasNext)
                    # then convert "0" to "False" and "1" to "True".
                    if (cond_param in intent_params and intent_params[cond_param].sort().name() == "Bool") or \
                       (cond_param in if_parameters and if_parameters[cond_param].sort().name() == "Bool"):
                        #print(cond_param)
                        pattern = rf'(Not\s*\(\s*)?\b{cond_param}\s*(==|!=)\s*(0|1)'
    
                        for idx, cond in enumerate(conditions):
                            match = re.search(pattern, cond)
                            if match:
                                cond_op = match.group(2)    # Capture == or !=
                                cond_value = match.group(3)  # Capture the 0 or 1
                                new_literal = "False" if cond_value == "0" else "True"

                                # Replace the matched 0/1 with False/True, keeping other parts intact
                                new_cond = re.sub(pattern, rf'\1{cond_param} {cond_op} {new_literal}', cond)
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
                #if var_name=="r4_3":
                #print("search_for_var_declaration: var_name: ", var_name, " ",variable, ", value: ", value)
                    
                if LENGTH_PATTERN.match(label) and var_name==LENGTH_PATTERN.match(label).group(1):
                    obj_name = LENGTH_PATTERN.match(label).group(3)
                    #if obj_name=="r4_2":
                    #    print(obj_name, " ", operator, " ", cond_value)
                    # Ensure the object is in our parameters dictionary
                    if obj_name not in intent_params and obj_name not in if_parameters:
                        # Infer its type from elsewhere or default to String
                        #if_parameters[obj_name] = String(obj_name)
                        var_condition = add_new_condition(graph, obj_name)
                    
                    if (obj_name in array_params and isinstance(array_params[obj_name], list)):
                        length = Int(f"{obj_name}_length")
                        if_parameters[f"{obj_name}_length"] = length
                        if invert_op and operator in INVERT_OPERATOR:
                            len_cond = f"{obj_name}_length {INVERT_OP_MAP[operator]} {cond_value}"
                        else:
                            len_cond = f"{obj_name}_length {operator} {cond_value}"
                    else:
                        if invert_op and operator in INVERT_OPERATOR:
                            len_cond = f"Length({obj_name}) {INVERT_OP_MAP[operator]} {cond_value}"
                        else:
                            len_cond = f"Length({obj_name}) {operator} {cond_value}"
                        length_condition = True

                    if not len_cond in conditions:
                        conditions.append(len_cond)
                    break

                elif ARRAY_DECLARATION_PATTER.match(label) and var_name==ARRAY_DECLARATION_PATTER.match(label).group("var_name"):
                    array_match = ARRAY_DECLARATION_PATTER.match(label)
                    array_type = "(" + array_match.group("type") + ")"
                    array_length = array_match.group("length")

                    array_elements = find_array_element_assignation(nodes, var_name, array_type, array_length) # array with z3 object of the array
                    if var_name not in if_parameters and var_name not in intent_params and var_name not in array_params:
                        array_params[var_name] = array_elements
                    
                    if f"length_{var_name}" not in if_parameters and f"length_{var_name}" not in intent_params:
                        if_parameters[f"length_{var_name}"] = Int(f"length_{var_name}")

                elif LENGTHOF_PATTERN.match(label) and var_name==LENGTHOF_PATTERN.match(label).group("var_name"):
                    # Array case (usually lengthof is used for the length of an array)
                    if var_name not in if_parameters and var_name not in intent_params:
                        if_parameters[var_name] = Int(var_name)

                elif STANDARD_VAR_DECLARATION_PATTERN.match(label) or \
                    PACKAGE_VAR_DECLARATION_PATTERN.match(label) or \
                    RETURN_VAR_ASSIGNATION_PATTERN.match(label) or \
                    THIS_VAR_ASSIGNATION_PATTERN.match(label) or \
                    THIS_VAR_DECLARATION_PATTERN.match(label):
                    
                    var, type = variable.split(" ", 1)
                        
                    if var not in if_parameters and var not in intent_params:
                        if_parameters[var] = infer_type(var, type)

                    if RETURN_VAR_ASSIGNATION_PATTERN.match(label):
                        return_match = RETURN_VAR_ASSIGNATION_PATTERN.match(label)
                        return_value = return_match.group("value")
                        if  return_value != "null":
                            add_new_condition(graph, return_match.group("value").lstrip("$"))
                        else:
                            if type[1:-1].replace(".", "_") in custom_types:
                                return_value = f"null_{type[1:-1].replace(".", "_")}"
                        var_condition = f"{var} == {return_value}"
                    elif THIS_VAR_ASSIGNATION_PATTERN.match(label):
                        #print("value_1: ", value)
                        add_new_condition(graph, value)
                        # If value is still not in the parameters array, add it 
                        if value not in if_parameters and value not in intent_params:
                            if_parameters[value] = infer_type(value, type)
                        var_condition = f"{var} == {value}"
                            #if var=="r11_3":
                            #    print(var_condition)
                    elif THIS_VAR_DECLARATION_PATTERN.match(label):
                        #print("value_2: ", value)
                        add_new_condition(graph, value)
                        var_condition = f"{var} == {value}"
                        #if var=="r0_this_mQuery_1":
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

                elif INSTACEOF_PATTERN.match(label):
                    instaceof_match = INSTACEOF_PATTERN.match(label)
                    variable = instaceof_match.group("variable")
                    if variable not in if_parameters and variable not in intent_params:
                        if_parameters[variable] = Bool(variable)

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
                        if (var2 in if_parameters and if_parameters[var2] == None) and\
                           (var1 in if_parameters and if_parameters[var1] != None):
                           sort = if_parameters[var1].sort()
                           if_parameters[var2] = Const(var2, sort) 
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
                        # Handle case where variable and object_name have different types (i.e. i have to change the type of object_name since there have to be a cast)
                        else:
                            if object_name in if_parameters and if_parameters[object_name].sort().name() == "Serializable":
                                if_parameters[object_name] = infer_type(object_name, type)
                            elif object_name in intent_params and intent_params[object_name].sort().name() == "Serializable":
                                intent_params[object_name] = infer_type(object_name, type)

                    else:
                        var_condition = f"{variable} == {value}"
                        type = None
                        # e.g.: r4 = m3 - case object = object
                        if (not value.isdigit() and
                            not (value.startswith('"') and value.endswith('"')) and 
                            not value=="null"):
                            add_new_condition(graph, value)
                            if value in if_parameters:
                                type = if_parameters.get(value).sort()
                            elif value in intent_params:
                                type = intent_params.get(value).sort() 
                        
                        if variable not in if_parameters and variable not in intent_params:
                            if type is not None:
                                if_parameters[variable] = infer_type(variable, type)
                            else:
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
        variable = variable.strip()
        value = value.strip()
        if variable not in if_parameters and variable not in intent_params:
            if_parameters[variable] = infer_type(variable, value)
        # Do not consider the case when a variable is equal to r0 (i.e. to the this object)
        if var_condition not in conditions and value != "r0":
            conditions.append(var_condition)

def find_array_element_assignation(nodes, array_name, array_type, array_length):
    result = [None] * int(array_length)
    for node, data in nodes:
        label = data.get('label','')
        match = ARRAY_ELEMENT_ASSIGNATIO_PATTERN.match(label)

        if match:
            if match.group("array_name") == array_name:
                idx = int(match.group("index"))
                if idx < int(array_length):
                    element_name = match.group("element_name").lstrip("$")
                    obj = infer_type(element_name, array_type)
                    result[idx] = obj
    return result


# Base directory containing APK subdirectories
base_dir = "./paths"
# Directory for all analysis results
results_dir = "./"
results_base = os.path.join(results_dir, "analysis_results")

def list_subdirs(dir_path):
    return [d for d in os.listdir(dir_path)
            if os.path.isdir(os.path.join(dir_path, d))]

def display_menu(options, prompt="Select an option:"):
    print(prompt)
    for idx, name in enumerate(options, start=1):
        print(f"{idx}. {name}")

def main():
    # Ensure results directory exists
    os.makedirs(results_base, exist_ok=True)

    # 1. List APK directories
    apks = list_subdirs(base_dir)
    if not apks:
        print(f"No subdirectories (APK folders) found in '{base_dir}'.")
        return

    # 2. User selects an APK directory
    selected_idx = None
    while selected_idx is None:
        display_menu(apks, prompt="Select the APK directory to analyze:")
        try:
            choice = int(input("\nEnter the number of the APK: "))
            if 1 <= choice <= len(apks):
                selected_idx = choice - 1
            else:
                print("Invalid selection. Please try again.\n")
        except ValueError:
            print("Invalid input. Please enter a valid number.\n")

    apk_name = apks[selected_idx]
    apk_dir = os.path.join(base_dir, apk_name)
    print(f"\nAnalyzing APK folder: {apk_dir}\n")

    # Create a specific results subdirectory for this APK
    apk_results_dir = os.path.join(results_base, apk_name)
    os.makedirs(apk_results_dir, exist_ok=True)

    # 3. Collect .dot files in selected APK folder
    dot_files = [f for f in os.listdir(apk_dir)
                 if f.endswith('.dot') and os.path.isfile(os.path.join(apk_dir, f))]
    if not dot_files:
        print(f"No .dot files found in '{apk_dir}'.")
        return

    # 4. Process each .dot file individually
    for dot_filename in dot_files:
        dot_path = os.path.join(apk_dir, dot_filename)
        print(f"Processing DOT file: {dot_filename}")

        # Prepare a dedicated output file for this .dot in the results directory
        base_name = os.path.splitext(dot_filename)[0]
        output_path = os.path.join(apk_results_dir, f"{base_name}_analysis_results.txt")
        seen_lines = set()

        with open(output_path, "w", encoding="utf-8") as output_file:
            # Metadata
            try:
                metadata = extract_metadata(dot_path)
            except Exception as e:
                print(f"Error extracting metadata: {e}")
                continue

            for key, value in metadata.items():
                output_file.write(f"{key}: {value}\n")
            output_file.write("\n")

            # Parse subgraphs
            try:
                subgraphs = parse_dot_file(dot_path)
            except Exception as e:
                print(f"Error parsing DOT file: {e}")
                continue

            # Analyze each path subgraph
            for idx, path_name in enumerate(subgraphs, start=1):
                parse_intent_params(subgraphs[path_name])
                parse_if(subgraphs[path_name])
                parameters = intent_params | if_parameters | array_params

                print(path_name)
                print("custom_types: ", custom_types)
                print("z3_contest: ", Z3_CONTEST)
                print("Conditions: ", conditions)
                print("Arrays: ", array_params)
                print("Parameters: ", parameters)
                print("Intent Parameters: ", intent_params)

                solver = Solver()
                for cond in conditions:
                    solver.add(eval(cond, Z3_CONTEST, parameters))

                if solver.check() == sat:
                    model = solver.model()
                    param_strings = []
                    for param, z3_var in sorted(intent_params.items()):
                        sort_name = z3_var.sort().name()
                        type_str = TYPE_MAPPING.get(sort_name, sort_name)
                        value = model[z3_var]
                        if value is None or (isinstance(value, str) and value == ""):
                            value_str = "[no lim]"
                        else:
                            if sort_name == "String":
                                v = str(value)
                                value_str = v if v != '"null"' else "[null]"
                            else:
                                value_str = str(value)
                        if sort_name == "Serializable":
                            serial_conds = [c for c in conditions if param in c]
                            if serial_conds:
                                value_str += " | Conditions: " + ", ".join(serial_conds)
                        param_name = param_name_map.get(param, param)
                        param_strings.append(f"{param_name} ({type_str}) : {value_str}")

                    line = " | ".join(param_strings)
                    if line not in seen_lines:
                        seen_lines.add(line)
                        output_file.write(f"{line}\n")
                    print(line)
                else:
                    print(f"{path_name}: No solution")
                print("-" * 50)
                reset_globals()

        print(f"Completed: results saved to '{output_path}'\n")

if __name__ == "__main__":
    main()
