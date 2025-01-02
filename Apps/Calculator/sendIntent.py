import subprocess

# Define the adb command to send the intent with extras
adb_command = [
    "adb", "shell", "am", "start",
    "-n", "com.example.calculator/.Calculator",
    "-a", "com.example.calculator.action.CALCULATE",
    "--ei", "n1", "16",  # Passing integer for n1
    "--ei", "n2", "5",   # Passing integer for n2
    "--es", "Op", "+"    # Passing string for the operator
]

# Execute the command
try:
    result = subprocess.run(adb_command, check=True, text=True, capture_output=True)
    print("Command executed successfully.")
    print("Output:", result.stdout)
except subprocess.CalledProcessError as e:
    print("Error executing command:", e)
    print("Error Output:", e.stderr)
