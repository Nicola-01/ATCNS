import re
from collections import deque
import networkx as nx
import pygraphviz as pgv

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

# Load and parse the DOT file
subgraphs = parse_dot_file("paths.dot")
path_1 = subgraphs["path_1"]

print("Variable declarations and their associated if conditions:")
for node in path_1.nodes(data=True):
    node_id = node[0]
    label = node[1].get('label', '').strip()
    
    if label.startswith('if '):
        # Extract variable and value from condition
        match = re.match(r'if\s+([^\s=]+)\s*==\s*([^\s]+)\s+goto', label)
        if not match:
            continue
            
        var_name = match.group(1)
        var_value = match.group(2)
        
        # Find where the variable was declared
        decl_node, decl_code = find_variable_declaration(path_1, node_id, var_name)
        
        print(f"If Node: {node_id}")
        print(f"Condition: {var_name} == {var_value}")
        if decl_node:
            print(f"Declaration found in node: {decl_node}")
            print(f"Declaration code: {decl_code}")
        else:
            print("No declaration found in preceding nodes")
        print("-" * 50)