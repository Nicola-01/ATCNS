import argparse
import time
from androguard.core.apk import APK
import os
import subprocess
import re
from itertools import product
import sys

from emulator import emulator_initialiser

    
DROZER_APK = APK("drozer-agent.apk")

#python3 send_intent.py ../Soot/z3/analysis_results.txt

def parse_file(file_path):  
    
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    
    metadata = {}
    intents = []
    metadata_pattern = re.compile(r"(apkFile|sdkVersion|package|activity|action):\s*(.+)")
    param_pattern = re.compile(r'(\w+)\s*\((\w+)\)\s*:\s*"?([^|\n"]+)"?')
    
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
                    values = ['""', '"abc"', '"123"', '"abc123"', '"abc 123"', '"abc@123"']
                elif type == 'integer':
                    values = [0, 1, -1, 2147483647, -2147483648]
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
    
def generate_combinations(intent):
    keys = [(param['name'], param['type']) for param in intent]
    values = [param['values'] for param in intent]
    
    extras = []
    
    for combination in product(*values):
        parts = []
        for (name, type_), value in zip(keys, combination):
            if value == "null":
                parts.append(f"--extra null {name}")
            else:
                parts.append(f"--extra {type_} {name} {value}")
        extra = " ".join(parts)
        extras.append(extra)
        
    return extras

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk_path", help="path to app apk file")
    parser.add_argument("values_path", help="path to relative apk intents values file")
    args = parser.parse_args()
    return args

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

def install(apk):
    print("Installing the app")
    while True:
        try:
            os.system("adb install -g {apk}".format(apk=apk.get_filename()))
            break
        except subprocess.CalledProcessError as err:
            print('[!] install failed')
            print(err)
            print('[!] retrying')
            
def stop(apk):
    os.system("adb shell am force-stop {package}".format(package=apk.get_package()))
            
def drozer_in_foreground():
    launch_app(DROZER_APK, False)     

def drozer_setup():
   
    if not check_app_installed(DROZER_APK):
        install(DROZER_APK)
    else:
        stop(DROZER_APK)
    
    launch_app(DROZER_APK)
    os.system("adb forward tcp:31415 tcp:31415")
    time.sleep(1)
    os.system("adb shell input tap 975 1800")
    

def main(args):
    
    # os.system("~/Android/Sdk/emulator/emulator -avd mobiotsec -no-audio -no-boot-anim -accel on -gpu swiftshader_indirect &")

    file_path = args.values_path
        
    # file_path = "../Soot/z3/analysis_results.txt"
        
    result = parse_file(file_path)

    metadata = result['metadata']
    apkFile = metadata['apkFile']
    sdkVersion = metadata['sdkVersion']
    package = metadata['package']
    activity = metadata['activity']   
    action = metadata['action']
    
    print("Lauching the emulator")
    emulator_initialiser(sdkVersion)
    
    intents = result['intents']
    # print(package, activity, action, intents)
    
    time.sleep(5)
    
    drozer_setup() 
    
    time.sleep(1)
    
    apk = APK(args.apk_path)
    check_app_installed(apk)
    uninstall(apk)
    install(apk)
    launch_app(apk)
    
    time.sleep(1)
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
            drozer_in_foreground()
            
            drozer_command_cpy = drozer_command
            drozer_command_cpy += extra
            drozer_command_cpy += "'"
                        
            print("\n" + "-" * 30 + "\n")
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
                
            time.sleep(3)        

if __name__ == "__main__":
    main(parse_args())
