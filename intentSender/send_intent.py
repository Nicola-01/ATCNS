import subprocess
import re
from itertools import product
import sys

#python3 send_intent.py ../Soot/z3/analysis_results.txt

def parse_intent_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Extract Package, Activity, and Action
    package = re.search(r'Package:\s*(\S+)', lines[0]).group(1)
    activity = re.search(r'Activity:\s*(\S+)', lines[1]).group(1)
    action = re.search(r'Action:\s*(\S+)', lines[2]).group(1)
    
    intents = []
    param_pattern = re.compile(r'(\w+)\s*\((\w+)\)\s*:\s*"?([^|\n"]+)"?')
    
    for line in lines[4:]:
        params = []
        for match in param_pattern.finditer(line):
            
            name = match.group(1)
            type = match.group(2)
            value = match.group(3).strip()
            values = []
        
            if value == '[no lim]':
                if type == 'string':
                    values = ['""', '"abc"', '"123"', '"abc123"', '"abc 123"', '"abc@123"']
                elif type == 'integer':
                    values = [0, 1, -1, 100, -100, 1000, -1000, 2147483647, -2147483648]
                elif type == 'boolean':
                    values = [True, False]
            else:
                values = [value]
                
            params.append({
                'name': name,
                'type': type,
                'values': values
            })
        
        intents.append(params)
    
    return {
        'Package': package,
        'Activity': activity,
        'Action': action,
        'Intents': intents
    }
    
def generate_combinations(intent):
    keys = [(param['name'], param['type']) for param in intent]
    values = [param['values'] for param in intent]
    
    extras = []
    
    for combination in product(*values):
        extra = " ".join(f"--extra {type_} {name} {value}" for (name, type_), value in zip(keys, combination))
        # print(extra)
        extras.append(extra)
        
    return extras

if len(sys.argv) != 2:
    print("Usage: python send_intent.py <fileDir>")
    sys.exit(1)

file_path = sys.argv[1]
    
# file_path = "../Soot/z3/analysis_results.txt"
    
result = parse_intent_file(file_path)

package = result['Package']
activity = result['Activity']   
action = result['Action']
intents = result['Intents']
# print(package, activity, action, intents)

get_PID = f"adb shell pidof {package}"
pid = subprocess.run(get_PID, shell=True, check=True, text=True, capture_output=True).stdout.strip()
# print("PID:", pid)



# # Add extras if needed
# for key, value in params.items():
#     for param, val in value.items():
#         drozer_command += f" --extra {key} {param} {val}"
        
drozer_command = (
    "drozer console connect --command 'run app.activity.start "
    f"--component {package} {activity} " )

if action != "":
    drozer_command += f"--action {action} "   

for intent in intents:
    extras = generate_combinations(intent)
    
    for extra in extras:
        drozer_command_cpy = drozer_command
        drozer_command_cpy += extra
        drozer_command_cpy += "'"
                    
        print("Drozer command:", drozer_command_cpy)

        # Execute the command
        try:
            subprocess.run("adb logcat -c", shell=True, check=True, text=True)
            result = subprocess.run(drozer_command_cpy, shell=True, check=True, text=True, capture_output=True)
            
            # print("Command executed successfully.")
            # print("Output:", result.stdout)

            get_logs = f"adb logcat --pid={pid} -d"
            # print("Get logs command:", get_logs)
            logs = subprocess.run(get_logs, shell=True, check=True, text=True, capture_output=True).stdout
            print(logs)
                
        except subprocess.CalledProcessError as e:
            print("Error executing command:", e)
            print("Error Output:", e.stderr)           
