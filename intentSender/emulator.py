import re
import subprocess
import os
import time

# Set your Android SDK path
ANDROID_SDK_ROOT = "~/Android/Sdk"
EMULATOR = f"~/Android/Sdk/emulator/emulator"

def run_command(command, wait_end=True, print_output=True):
    """Executes a shell command and prints output in real-time"""
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    if print_output:

        for line in iter(process.stdout.readline, ''):
            print(line, end='')  # Print output as it arrives

        for line in iter(process.stderr.readline, ''):
            print(line, end='')  # Print errors as they arrive

        process.stdout.close()
        process.stderr.close()
    
    if wait_end:
        process.wait()  # Ensure the process completes
        

def get_available_sdk_images():
    """
    Returns a dict mapping SDK version to system image path, e.g.:
    {
        10: "system-images;android-10;google_apis;x86",
        33: "system-images;android-33;google_apis;x86_64"
    }
    """
    
    cmd = "sdkmanager --list | grep 'system-images;android-[0-9]\\+;google_apis;'"
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    
    if result.returncode != 0:
        print("Failed to list SDK images")
        return {}

    sdk_map = {}
    pattern = re.compile(r"system-images;android-(\d+);google_apis;(x86_64|x86)")
    
    for line in result.stdout.splitlines():
        match = pattern.search(line)
        if match:
            sdk_version = int(match.group(1))
            arch = match.group(2)
            sdk_key = f"system-images;android-{sdk_version};google_apis;{arch}"
            # Always prefer x86_64 if multiple entries exist
            if sdk_version not in sdk_map or arch == "x86_64":
                sdk_map[sdk_version] = sdk_key

    return sdk_map

def get_system_image_arch(sdk_version):
    """Check available system image architecture (x86_64 or x86)"""
    result = subprocess.run(f"sdkmanager --list | grep 'system-images;android-{sdk_version}' | grep 'google_apis'", shell=True, capture_output=True, text=True)
    if result.returncode == 0:
        if "x86_64" in result.stdout:
            return "x86_64"
        elif "x86" in result.stdout:
            return "x86"
    return None

def install_system_image(sdk_image,):
    """Installs the Android system image"""
    print(f"Installing Android {sdk_image} system image...")
    run_command(f"sudo sdkmanager \"{sdk_image}\"")

def create_avd(sdk_version, sdk_image):
    """Creates a new AVD"""
    print(f"Creating AVD emulator_{sdk_version}...")
    run_command(f"avdmanager create avd -n emulator_{sdk_version} -k \"{sdk_image}\" -d pixel")
    
    USER_HOME = os.path.expanduser("~")
    print(f"Moving AVD to {USER_HOME}/Android/Sdk/system-images/android-{sdk_version}...")
    run_command(f"sudo mv /lib/android-sdk/system-images/android-{sdk_version} $HOME/Android/Sdk/system-images/android-{sdk_version}")

def start_emulator(sdk_version):
    """Starts the Android emulator"""
    print(f"Starting emulator emulator_{sdk_version}...")
    run_command(f"{EMULATOR} -avd emulator_{sdk_version} -no-snapshot-load -no-audio -no-boot-anim -accel on -gpu swiftshader_indirect &", False, False)
    
def check_emulator_exists(sdk_version):
    """Checks if the emulator already exists"""
    EMULATOR_NAME = f"emulator_{sdk_version}"
    
    try:
        emulator_path = os.path.expanduser("~/Android/Sdk/emulator/emulator")
        result = subprocess.run([emulator_path, "-list-avds"], capture_output=True, text=True, check=True)
        output = result.stdout
        
        # Parse the output to find the emulator
        lines = output.split("\n")
        for i in range(len(lines)):
            if lines[i].strip() == EMULATOR_NAME:
                return True
        
    except subprocess.CalledProcessError as e:
        print("Error executing avdmanager:", e)
    
    return False

def is_emulator_online():
    """Checks if any emulator is online"""
    try:
        result = subprocess.run("adb devices", shell=True, capture_output=True, text=True, check=True)
        lines = result.stdout.strip().split("\n")
        for line in lines[1:]:  # Skip the first line: "List of devices attached"
            if line.startswith("emulator-") and "\tdevice" in line:
                return True
    except subprocess.CalledProcessError as e:
        print("Failed to check ADB devices:", e)
    return False

def closeAllEmulators():
    """Closes all running emulators"""
    try:
        result = subprocess.run("adb devices | grep emulator | cut -f1 | while read line; do adb -s $line emu kill; done", shell=True, capture_output=True, text=True, check=True)
        time.sleep(5)
    except subprocess.CalledProcessError as e:
        print("Failed to close emulators:", e)

def emulator_initialiser(sdk_version):
    """Initializes and starts the emulator"""
    
    # Close all running emulators
    closeAllEmulators()
    

    sdk_map = get_available_sdk_images()
    max_sdk_version = max(sdk_map.keys())
    while sdk_version not in sdk_map:
        print(f"System image not found for Android SDK {sdk_version}. Trying next version...")
        sdk_version += 1
        if sdk_version > max_sdk_version:
            print(f"No available system image found for Android SDK {sdk_version}. Aborting...")
            return
    
    # First, check if the emulator already exists
    already_exists = check_emulator_exists(sdk_version)
    if already_exists:
        print(f"Emulator emulator_{sdk_version} already exists.")
        
    # Proceed with installation, AVD creation, and starting the emulator
    if not already_exists:         
        install_system_image(sdk_map[sdk_version])
        print("\n--------------------\n")
        create_avd(sdk_version, sdk_map[sdk_version])
        print("\n--------------------\n")
    start_emulator(sdk_version)
    
    for _ in range(30):  # wait up to 30 * 3 = 90 seconds
        if is_emulator_online():
            print("Emulator is online.")
            break
        time.sleep(3)
    else:
        print("Emulator did not come online in time.")
    
    return f"emulator_{sdk_version}"

# Example usage
# emulator_initialiser(4) # versions: 10, 15 - 19, 21 - 36
