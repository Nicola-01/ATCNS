import subprocess

package = "com.example.calculator"
activity = "com.example.calculator.Calculator"
action = "com.example.calculator.action.CALCULATE"

params = {
    "integer": {"n1": 32, "n2": 10},
    "string": {"Op": "+"},
}

get_PID = f"adb shell pidof {package}"
pid = subprocess.run(get_PID, shell=True, check=True, text=True, capture_output=True).stdout.strip()
# print("PID:", pid)

drozer_command = (
    "drozer console connect --command 'run app.activity.start "
    f"--component {package} {activity} "
    f"--action {action} "
)

# Add extras if needed
for key, value in params.items():
    for param, val in value.items():
        drozer_command += f" --extra {key} {param} {val}"

drozer_command += "'"
        
print("Drozer command:", drozer_command)

# Execute the command
try:
    subprocess.run("adb logcat -c", shell=True, check=True, text=True)
    result = subprocess.run(drozer_command, shell=True, check=True, text=True, capture_output=True)
    
    print("Command executed successfully.")
    print("Output:", result.stdout)

    get_logs = f"adb logcat --pid={pid} -t 100"
    print("Get logs command:", get_logs)
    logs = subprocess.run(get_logs, shell=True, check=True, text=True, capture_output=True).stdout
    print(logs)
    
except subprocess.CalledProcessError as e:
    print("Error executing command:", e)
    print("Error Output:", e.stderr)
