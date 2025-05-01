import subprocess
import re
from itertools import product
from emulator import start_emulator
import sys

def parse_intent_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    package = re.search(r'package:\s*(\S+)', lines[2]).group(1)
    activity = re.search(r'activity:\s*(\S+)', lines[3]).group(1) if len(lines) > 1 else ""
    action = re.search(r'action:\s*(\S+)', lines[4]).group(1) if len(lines) > 2 else ""
    
    intents = []
    param_pattern = re.compile(r'(\w+)\s*\((\w+)\)\s*:\s*"?([^|\n"]+)"?')
    
    for line in lines[4:]:
        params = []
        for match in param_pattern.finditer(line):
            name = match.group(1)
            type_ = match.group(2)
            value = match.group(3).strip()
            values = []
        
            if value == '[no lim]':
                if type_ == 'string':
                    values = ['""', '"abc"', '"123"', '"abc123"', '"abc 123"', '"abc@123"']
                elif type_ == 'integer':
                    values = [0, 1, -1, 100, -100, 1000, -1000, 2147483647, -2147483648]
                elif type_ == 'boolean':
                    values = ['true', 'false']
            else:
                values = [value]
                
            params.append({
                'name': name,
                'type': type_,
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
        extras.append(extra)
        
    return extras

def get_exported_components(package):
    components = []
    # Get exported activities
    activity_cmd = f'drozer console connect --command "run app.activity.info -a {package} -f"'
    result = subprocess.run(activity_cmd, shell=True, capture_output=True, text=True)
    activities = re.findall(rf'{re.escape(package)}/[\w.]+', result.stdout)
    components.extend([('activity', a) for a in activities])
    
    # Get exported services
    service_cmd = f'drozer console connect --command "run app.service.info -a {package} -f"'
    result = subprocess.run(service_cmd, shell=True, capture_output=True, text=True)
    services = re.findall(rf'{re.escape(package)}/[\w.]+', result.stdout)
    components.extend([('service', s) for s in services])
    
    # Get exported receivers
    receiver_cmd = f'drozer console connect --command "run app.broadcast.info -a {package} -f"'
    result = subprocess.run(receiver_cmd, shell=True, capture_output=True, text=True)
    receivers = re.findall(rf'{re.escape(package)}/[\w.]+', result.stdout)
    components.extend([('receiver', r) for r in receivers])
    
    return components

def has_exception(logs, package):
    for line in logs.split('\n'):
        if package in line and ('Exception' in line or 'Error' in line):
            return True
    return False

# if len(sys.argv) != 2:
#     print("Usage: python send_intent.py <fileDir>")
#     sys.exit(1)

# file_path = sys.argv[1]

file_path = "../Soot/z3/analysis_results.txt"


result = parse_intent_file(file_path)
package = result['Package']
action = result['Action']
intents = result['Intents']

# Get PID once (may need to refresh if app restarts)
get_pid = f"adb shell pidof {package}"
pid = subprocess.run(get_pid, shell=True, text=True, capture_output=True).stdout.strip()

components = get_exported_components(package)

for component_type, full_component in components:
    if '/' in full_component:
        _, component_name = full_component.split('/', 1)
    else:
        component_name = full_component
    
    # Build base Drozer command
    if component_type == 'activity':
        base_cmd = f"run app.activity.start --component {package} {component_name}"
    elif component_type == 'service':
        base_cmd = f"run app.service.start --component {package} {component_name}"
    elif component_type == 'receiver':
        base_cmd = f"run app.broadcast.send --component {package} {component_name}"
    else:
        continue
    
    if action:
        base_cmd += f" --action {action}"
    
    for intent in intents:
        extras = generate_combinations(intent)
        for extra in extras:
            full_cmd = f"{base_cmd} {extra}"
            drozer_command = f"drozer console connect --command '{full_cmd}'"
            
            try:
                subprocess.run("adb logcat -c", shell=True, check=True)
                subprocess.run(drozer_command, shell=True, check=True)
                # Capture logs
                log_cmd = f"adb logcat --pid={pid} -d"
                logs = subprocess.run(log_cmd, shell=True, capture_output=True, text=True).stdout
                
                if has_exception(logs, package):
                    print(f"[!] Exception detected in {component_type} {component_name}")
                    print(f"    Command: {drozer_command}")
                    print("    Logs snippet:")
                    for line in logs.split('\n')[-10:]:  # Show last 10 lines
                        if package in line:
                            print(f"    {line.strip()}")
            except subprocess.CalledProcessError as e:
                print(f"Error executing command: {e}")