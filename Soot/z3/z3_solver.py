import re
from collections import deque
import networkx as nx
import pygraphviz as pgv

from z3 import *

def parse_dot_file(dot_path):
    """Reads the DOT file and returns a dictionary of NetworkX graphs."""
    A = pgv.AGraph(dot_path)
    return {sub.name: nx.DiGraph(sub) for sub in A.subgraphs()}

def find_variable_declaration(graph, start_node, target_var):
    """
    Traverse the graph backward from start_node to find
    the node where target_var was declared/assigned.
    """
    visited = set()
    queue = deque([start_node])
    
    while queue:
        current_node = queue.popleft()
        if current_node in visited:
            continue
        visited.add(current_node)
        
        # Check current node's label for variable assignment
        node_data = graph.nodes[current_node]
        label = node_data.get('label', '')
        
        # Split multi-line labels
        for line in label.split('\\l'):
            line = line.strip()
            # Match assignment patterns like "b4 = ..." or "i2 (int) = ..."
            if re.match(rf'^\s*{re.escape(target_var)}\s*(\(.*?\))?\s*=', line):
                return current_node, line.strip()
        
        # Add predecessors to continue searching backward
        queue.extend(graph.predecessors(current_node))
    
    return None, None

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
        return Float(variable, Float32())
    except ValueError:
        pass

    # If none of the above, return "unknown"
    return "unknown"


# Load and parse the DOT file
subgraphs = parse_dot_file("paths.dot")

for i in range(1,len(subgraphs)):
    
    pathName = f"path_{i}"
    print(f"Solution for {pathName}")

    # print("Variable declarations and their associated if conditions:")

    parameters = {}

    parameters.update({"i0": Int('i0')})
    parameters.update({"i1": Int('i1')})
    parameters.update({"r2": String('r2')})

    solver = Solver()

    for node in subgraphs[pathName].nodes(data=True):
        node_id = node[0]
        label = node[1].get('label', '').strip()
        
        # if label == "$m1 = $i1": 
        #     break
        
        # print(node_id + " " + label)
        
        if label.startswith('if '):
            condition = label.replace(" == ", "==").split(' ')[1].replace("$","")
            
            for key, value in parameters.items():
                condition = condition.replace(key, f"parameters.get('{key}')")
            
                        
            for successor in subgraphs[pathName].successors(node_id):
                edge_data = subgraphs[pathName].get_edge_data(node_id, successor)
                edge_label = edge_data.get('label', '') if edge_data else ''
                
                if edge_label == "false":
                    condition = f"Not({condition})"
            
            solver.add(eval(condition)) 
        elif (' = ') in label and len(label.split(' = ')) == 2:
            variable, value = label.split(' = ', 1)
            variable = variable.replace("$","")
            if len(variable.split(' ')) == 1 and len(value.split(' ')) == 1:
                parameters.update({variable: infer_type(variable, value)})       
                condition = f"parameters.get('{variable}') == {value}"
                solver.add(eval(condition))
            

    if solver.check() == sat:
        model = solver.model()
        print(f"n1 = {model[parameters.get('i0')]}")
        print(f"n2 = {model[parameters.get('i1')]}")
        print(f"Op = {model[parameters.get('r2')]}")
    else:
        print("No solution found.")
    print("-"*50)
    