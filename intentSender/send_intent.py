import argparse
import json
import time
from androguard.core.apk import APK
import os
import subprocess
import re
from itertools import product
import sys
from loguru import logger

from emulator import closeAllEmulators, emulator_initialiser
from LLMquery import query_groq

# Configure logger
logger.remove()
logger.add(sys.stderr, level="WARNING")

# Constants
DROZER_APK = APK("drozer-agent.apk")
DROZER_MIN_VERSION = 17

# Global state
json_responses = []
emulator_is_installed = False

# These will be assigned per analysis file
global metadata, apkFile, sdkVersion, package, activity, action, intents, start


def parse_file(file_path):  
    """
    Parse the Z3 analysis result file to extract APK metadata and intent definitions.
    """
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    metadata = {}
    intents = []
    metadata_pattern = re.compile(r"(apkFile|sdkVersion|package|activity|action):\s*(.+)")
    param_pattern = re.compile(r'([\w.]+)\s*\((\w+)\)\s*:\s*("?[^|\n"]*"?)')

    for line in lines:
        match = re.match(metadata_pattern, line)
        if match:
            key, value = match.groups()
            metadata[key] = value.strip()

        params = []
        for match in param_pattern.finditer(line):
            name = match.group(1)
            type = match.group(2)
            value = match.group(3).strip()
            values = []

            if value == '[no lim]':
                if type == 'string':
                    values = ['""', '"abc"', '"!abc@ 123"']
                elif type == 'integer':
                    values = [0, 2147483647, -2147483648]
                elif type == 'boolean':
                    values = [True, False]
            else:
                values = [value]

            params.append({
                'name': name,
                'type': type,
                'values': values
            })

        if len(params) > 0:
            intents.append(params)

    return {
        'metadata': metadata,
        'intents': intents
    }

def generate_combinations(intents):
    """
    Generate all valid combinations of intent extras based on Z3 output.
    """
    supported_types = ['string', 'integer', 'boolean']
    extras = []

    for intent in intents:
        keys = [(p['name'], p['type']) for p in intent]
        values = [p['values'] for p in intent]

        for combination in product(*values):
            combinations = []
            for (name, type_), value in zip(keys, combination):
                if type_ not in supported_types:
                    print(f"Unsupported type: {type_}")
                    continue
                if value == '[null]':
                    continue
                combinations.append({
                    'name': name,
                    'type': type_,
                    'value': value
                })
            if len(combinations) > 0:
                extras.append(combinations)

    return extras

def parse_args():
    """
    Parse command-line arguments.
    Returns:
        Namespace: Contains apk_path and analysis_path.
    """
    parser = argparse.ArgumentParser()
    parser.add_argument("apk_path", help="Path to app apk file")
    parser.add_argument("analysis_path", help="Directory containing z3's analysis for the app")
    return parser.parse_args()

def check_app_installed(apk):
    """
    Check if the APK is already installed on the emulator.
    """
    if (os.system("adb shell pm list packages | grep {package}".format(package=apk.get_package())) == 0):
        print("App already installed")
        return True
    else:
        print("App not installed")
        return False

def launch_app(apk, verbose=True):
    """
    Launch the main activity of the APK on the emulator.
    Args:
        apk (APK): Parsed APK object
        verbose (bool): Whether to print launch info
    """
    if verbose:
        print(f"Launching the app {apk.get_filename()}")
    mainactivity = "{}/{}".format(apk.get_package(), apk.get_main_activity())
    os.system("adb shell am start -n {act}".format(act=mainactivity))

def uninstall(apk):
    """
    Uninstall the APK from the emulator.
    """
    if check_app_installed(apk):
        print(f"Uninstalling the app {apk.get_filename()}")
        subprocess.call(["adb", "uninstall", apk.get_package()], stdout=subprocess.DEVNULL)

def install(apk, sdk_version):
    """
    Install the APK on the emulator.
    Grants runtime permissions for SDK >= 23.
    """
    print("Installing the app")
    
    install_cmd = f"adb install"
    if sdk_version is not None and int(sdk_version) >= 23:
        install_cmd += " -g"  # Grant all permissions at install time
    install_cmd += f" {apk.get_filename()}"
    
    while True:
        try:
            os.system(install_cmd)
            break
        except subprocess.CalledProcessError as err:
            print('[!] install failed')
            print(err)
            print('[!] retrying')

def stop(apk):
    """
    Force stop the app on the emulator.
    """
    os.system("adb shell am force-stop {package}".format(package=apk.get_package()))

def drozer_in_foreground():
    """Bring the Drozer agent to the foreground."""
    launch_app(DROZER_APK, False)

def drozer_setup(sdkVersion):
    """Ensure the Drozer agent is installed, launched, and connected."""
    if not check_app_installed(DROZER_APK):
        install(DROZER_APK, sdkVersion)
    else:
        stop(DROZER_APK)

    launch_app(DROZER_APK)
    os.system("adb forward tcp:31415 tcp:31415")
    time.sleep(1)
    os.system("adb shell input tap 975 1800")

def send_drozer_intent(package, sdk_version, activity, action, extra):
    """Build and send the intent using Drozer."""
    drozer_in_foreground()
    drozer_command = (
        "drozer console connect --command 'run app.activity.start "
        f"--component {package} {activity} "
    )
    if action:
        drozer_command += f"--action {action} "

    for item in extra:
        drozer_command += f"--extra {item['type']} {item['name']} {item['value']} "

    drozer_command += "'"
    get_logs(drozer_command)

def send_adb_intent(package, sdk_version, activity, action, extra):
    """Build and send the intent using ADB."""
    adb_command = f"adb shell am start -n {package}/{activity}"
    if action:
        adb_command += f" -a {action}"

    for item in extra:
        intentType = ''
        if item['type'] == 'string':
            intentType = 's'
        elif item['type'] == 'integer':
            intentType = 'i'
        elif item['type'] == 'boolean':
            intentType = 'z'
        adb_command += f" --e{intentType} {item['name']} {item['value']}"

    get_logs(adb_command)

def get_pid(package, sdk_version):
    """Retrieve the PID of the app process running on the emulator."""
    if int(sdk_version) >= 21:
        cmd = f"adb shell pidof {package}"
    else:
        cmd = f"adb shell ps | grep {package}"

    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)

    if result.returncode != 0 or not result.stdout.strip():
        return None

    if int(sdk_version) >= 21:
        return result.stdout.strip()
    else:
        parts = result.stdout.strip().split()
        if len(parts) >= 2:
            return parts[1]  # Assume PID is second column
        else:
            return None

def send_intents(apk_path, file_path):
    """Orchestrate the full workflow: boot emulator, install app, send all intent combinations."""
    global metadata, apkFile, sdkVersion, package, activity, action, intents
    result = parse_file(file_path)

    metadata = result['metadata']
    apkFile = metadata['apkFile']
    sdkVersion = int(metadata['sdkVersion'])
    package = metadata['package']
    activity = metadata['activity']   
    action = metadata['action']
    intents = result['intents']

    if not apk_path.endswith(".apk"):
        if not apk_path.endswith("/"):
            apk_path += "/"
        apk_path = apk_path + apkFile

    use_drozer = sdkVersion >= DROZER_MIN_VERSION

    global emulator_is_installed
    global pid
    if not emulator_is_installed:
        emulator_is_installed = True
        print("[~] Launching emulator")
        emulator_initialiser(sdkVersion)

        if use_drozer:
            print("[~] Drozer agent setup")
            drozer_setup(sdkVersion)
            time.sleep(1)
            input("[!] On the Drozer app on the device, set 'Embedded Server' to 'ON', then press Enter to continue...")

        time.sleep(3)

        print("[~] Installing the app")
        apk = APK(apk_path)
        uninstall(apk)
        install(apk, sdkVersion)
        launch_app(apk)
        time.sleep(5)

        pid = get_pid(package, sdkVersion)
        global start
        start = time.time()

    extras = generate_combinations(intents)

    if len(extras) == 0:
        print("\n[!] No compatible extras found in the analysis file.")
        return

    file_name = os.path.basename(file_path)
    print(f"\n=== Sending intents from: {file_name} ===\n")    
    for extra in extras:
        for item in extra:
            print(f"Extra: {item['name']} ({item['type']}) = {item['value']}")

        if use_drozer:
            send_drozer_intent(package, sdkVersion, activity, action, extra)
        else:
            send_adb_intent(package, sdkVersion, activity, action, extra)
        time.sleep(1)
        
        print("\n" + "-" * 30 + "\n")

def get_logs(command):
    """
    Executes the intent command, captures logcat output, and uses the LLM to analyze it.

    Args:
        command (str): The full adb or drozer command to execute.
    """
    print("Intent command:", command)
    
    try:
        # Clear log buffer before intent execution
        subprocess.run("adb logcat -c", shell=True, check=True, text=True)

        # Run the intent command (ADB or Drozer)
        subprocess.run(command, shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # Wait a moment for logs to populate
        time.sleep(2)

        # Dump logs from buffer
        result = subprocess.run("adb logcat -d", shell=True, check=True, text=True, capture_output=True)
        lines = result.stdout.splitlines()

        # Filter logs: include only those related to the package or PID
        logs = [line for line in lines if any(tag in line for tag in [pid])] # package
        logs = "\n".join(logs)

        # Send filtered logs + command to the LLM
        response = query_groq(package, command, logs)

        # Parse and store the structured JSON result
        try:
            parsed = json.loads(response)
            json_responses.append(parsed)
            print("Has an error:", parsed.get("error", "Invalid JSON: missing 'error'"))
        except json.JSONDecodeError:
            print("Invalid JSON returned by LLM:")
            print(response)

    except subprocess.CalledProcessError as e:
        print("Error getting crash logs:", e)


def main(args):
    """
    Entry point: processes all analysis files in a directory, sends intents, and writes results.
    """
    input_dir = args.analysis_path
    apk_path = args.apk_path

    for fname in sorted(os.listdir(input_dir)):
        if not fname.endswith(".txt"):
            continue

        file_path = os.path.join(input_dir, fname)

        try:
            send_intents(apk_path, file_path)
        except Exception as e:
            print(f"[!] Error processing {fname}: {e}")
            
    # Close all emulators

    execution_time = time.time() - start
    
    print("\n[~] Closing all emulators")
    closeAllEmulators()

    # Save all LLM responses into a single results file
    with open(f"intent_results_{apkFile}.json", "w", encoding="utf-8") as f:
        json.dump(json_responses, f, indent=2)

    # Compute and print metrics
    total = len(json_responses)
    errors = sum(1 for r in json_responses if r.get("error") is True)
    percentage = (errors / total) * 100 if total > 0 else 0

    print("\n=== INTENT TEST SUMMARY ===")
    print(f"Total execution time: {execution_time:.2f} seconds")
    print(f"Total intents sent: {total}")
    print(f"Errors detected: {errors} ({percentage:.2f}%)")

if __name__ == "__main__":
    main(parse_args())
