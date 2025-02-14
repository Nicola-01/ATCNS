import pygraphviz as pgv

# Load the DOT file
graph = pgv.AGraph("graph.dot")

# Access nodes and edges, including the label
for node in graph.nodes():
    label = graph.get_node(node).attr.get('label', 'No label')
    print(f"Node: {node} : {label}")

for edge in graph.edges():
    print(f"Edge: {edge}")
