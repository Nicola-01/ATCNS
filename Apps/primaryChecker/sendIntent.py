import subprocess as subp
import time

# Define the adb command to send the intent with extras
adb_command = [
    "adb", "shell", "am", "start",
    "-n", "com.example.primarychecker/.PrimaryChecker",
    "-a", "com.example.primarychecker.action.PRIMARYCHECKER",
    "--ei", "number", "16",  # Passing integer
]

# Execute the first command
try:
    result = subp.run(adb_command, check=True, text=True, capture_output=True)
    print("Intent sent successfully.")
    print("Output:", result.stdout)
except subp.CalledProcessError as e:
    print("Error sending intent:", e)
    print("Error Output:", e.stderr)
    exit(1)

# Retrieve the process ID for filtering logs
try:
    ps_command = ["adb", "shell", "ps"]
    ps_result = subp.run(ps_command, check=True, text=True, capture_output=True)
    process_lines = ps_result.stdout.splitlines()
    pid = None
    for line in process_lines:
        if "com.example.primarychecker" in line:
            pid = line.split()[1]  # Assuming the PID is the second column
            break

    if pid is None:
        print("Error: Could not find PID for com.example.primarychecker")
        exit(1)

    print(f"PID found: {pid}")

    # Filter logs by PID and set a timeout
    logcat_command = ["adb", "logcat"]
    log_process = subp.Popen(logcat_command, stdout=subp.PIPE, text=True)

    print("Listening to logs... (Press Ctrl+C to stop)")

    last_result = None  # To store the last "Result:" line
    start_time = time.time()
    timeout = 1  # Set the timeout duration (seconds)

    while True:
        line = log_process.stdout.readline()
        if not line:
            break

        if "Result:" in line:
            last_result = line.strip()

        # Stop after the timeout duration
        if time.time() - start_time > timeout:
            if last_result:
                print("Last Result:", last_result)
            else:
                print("No result found within the timeout period.")
            print("Log listening timed out.")
            log_process.terminate()
            break

except subp.CalledProcessError as e:
    print("Error retrieving or processing logs:", e)
except KeyboardInterrupt:
    print("Log listening stopped.")
