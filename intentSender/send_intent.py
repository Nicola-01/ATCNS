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

from emulator import emulator_initialiser
from LLMquery import query_groq

    
logger.remove()
logger.add(sys.stderr, level="WARNING")
DROZER_APK = APK("drozer-agent.apk")
DROZER_MIN_VERSION = 17

global metadata, apkFile, sdkVersion, package, activity, action, intents
global emulator_is_installed, use_drozer, pid, json_responses

json_responses = []
emulator_is_installed = False

#python3 send_intent.py ../Soot/z3/analysis_results.txt

def parse_file(file_path):  
    
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
    
    supported_types = ['string', 'integer', 'boolean'] # todo: add more types
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
                    continue  # omit the extra entirely
                # elif value == '':
                #     parts.append(f'--extra {type_} {name} ""')
                else:
                    combinations.append({
                        'name': name,
                        'type': type_,
                        'value': value
                    })
            if len(combinations) > 0:
                extras.append(combinations)
                
    return extras

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk_path", help="Path to app apk file")
    parser.add_argument("analysis_path", help="Directory containing z3's analysis for the app")
    return parser.parse_args()

def check_app_installed(apk):
    if (os.system("adb shell pm list packages | grep {package}".format(package=apk.get_package())) == 0):
        print("App already installed")
        return True
    else:
        print("App not installed")
        return False

def launch_app(apk, verbose=True):
    if verbose:
        print(f"Lauching the app {apk.get_filename()}")
    mainactivity = "{}/{}".format(apk.get_package(), apk.get_main_activity())
    os.system("adb shell am start -n {act}".format(act=mainactivity))

def uninstall(apk):
    if check_app_installed(apk):
        print(f"Uninstalling the app {apk.get_filename()}")
        subprocess.call(["adb", "uninstall", apk.get_package()], stdout=subprocess.DEVNULL)

def install(apk, sdk_version):
    print("Installing the app")
    
    install_cmd = f"adb install"
    if sdk_version is not None and int(sdk_version) >= 23:
        install_cmd += " -g"
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
    os.system("adb shell am force-stop {package}".format(package=apk.get_package()))
            
def drozer_in_foreground():
    launch_app(DROZER_APK, False)     

def drozer_setup(sdkVersion):
   
    if not check_app_installed(DROZER_APK):
        install(DROZER_APK, sdkVersion)
    else:
        stop(DROZER_APK)
    
    launch_app(DROZER_APK)
    os.system("adb forward tcp:31415 tcp:31415")
    time.sleep(1)
    os.system("adb shell input tap 975 1800")
    
def send_drozer_intent(package, sdk_version, activity, action, extra, pid):
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

def send_adb_intent(package, sdk_version, activity, action, extra, pid):
    
    # adb_command = [
    #     "adb", "shell", "am", "start",
    #     "-n", "com.example.calculator/.Calculator",
    #     "-a", "com.example.calculator.action.CALCULATE",
    #     "--ei", "n1", "16",  # Passing integer for n1
    #     "--ei", "n2", "5",   # Passing integer for n2
    #     "--es", "Op", "+"    # Passing string for the operator
    # ]
    
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
        # assume ps output: user pid ppid ... name
        parts = result.stdout.strip().split()
        if len(parts) >= 2:
            return parts[1]  # PID is usually the second column
        else:
            return None
    
def send_intents(apk_path, file_path):
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
        print("Lauching the emulator")
        emulator_initialiser(sdkVersion)
        time.sleep(3)
        
        if use_drozer:
            drozer_setup(sdkVersion)
            time.sleep(1)
        time.sleep(3)
              
        apk = APK(apk_path)
        uninstall(apk)
        install(apk, sdkVersion)
        launch_app(apk)
        time.sleep(5)
            
        pid = get_pid(package, sdkVersion)
    
    extras = generate_combinations(intents)
        
    if len(extras) == 0:
        print("No compatible extras found in the analysis file.")
        return
    
    file_name = os.path.basename(file_path)
    print(f"\n===== Sending intents from: {file_name} =====")    
    for extra in extras:
        print("\n" + "-" * 30 + "\n")
        
        for item in extra:
            print(f"Extra: {item['name']} ({item['type']}) = {item['value']}")
        
        if use_drozer:
            send_drozer_intent(package, sdkVersion, activity, action, extra, pid)
        else:
            send_adb_intent(package, sdkVersion, activity, action, extra, pid)
        time.sleep(1)     
    
def get_logs(command):
    
    print("Intent command:", command)
    
    try:
        subprocess.run("adb logcat -c", shell=True, check=True, text=True)
        subprocess.run(command, shell=True, check=True, text=True, capture_output=True)
        time.sleep(2)
        result = subprocess.run("adb logcat -d", shell=True, check=True, text=True, capture_output=True)
        lines = result.stdout.splitlines()
        # logs = [line for line in lines if any(tag in line for tag in ["AndroidRuntime", "ActivityManager", package])]
        # logs = [line for line in lines if any(tag in line for tag in [package])]
        logs = [line for line in lines if any(tag in line for tag in [pid, package])]
        logs = "\n".join(logs)
        # print("\nLOGS:\n", logs)
        
        response = query_groq(package, command, logs)
        
        try:
            parsed = json.loads(response)
            json_responses.append(parsed)
            print("Was an error: ", parsed.get("error", "Invalid JSON: missing 'error'"))
        except json.JSONDecodeError:
            print("Invalid JSON returned by LLM:")
            print(response)
            
        # print("\nRESPONSE:\n", response)
        
        
    except subprocess.CalledProcessError as e:
        print("Error getting crash logs:", e)


def main(args):
    input_dir = args.analysis_path
    apk_path = args.apk_path
    
    for fname in sorted(os.listdir(input_dir)):
        if not fname.endswith(".txt"):
            continue
        
        file_path = os.path.join(input_dir, fname)
        
        # try:
        send_intents(apk_path, file_path)
        # except Exception as e:
        #     print(f"[!] Error processing {fname}: {e}")

    with open("intent_results.json", "w", encoding="utf-8") as f:
        json.dump(json_responses, f, indent=2)
    

if __name__ == "__main__":
    main(parse_args())
