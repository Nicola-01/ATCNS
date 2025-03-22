import subprocess
import os

# Set your Android SDK path
ANDROID_SDK_ROOT = "~/Android/Sdk"
EMULATOR = f"~/Android/Sdk/emulator/emulator"

# Configurations
DEVICE_TYPE = "pixel"

def run_command(command):
    """Executes a shell command and prints output in real-time"""
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    for line in iter(process.stdout.readline, ''):
        print(line, end='')  # Print output as it arrives

    for line in iter(process.stderr.readline, ''):
        print(line, end='')  # Print errors as they arrive

    process.stdout.close()
    process.stderr.close()
    process.wait()  # Ensure the process completes

def get_system_image_arch(sdk_version):
    """Check available system image architecture (x86_64 or x86)"""
    result = subprocess.run(f"sdkmanager --list | grep 'system-images;android-{sdk_version}' | grep 'google_apis'", shell=True, capture_output=True, text=True)
    if result.returncode == 0:
        if "x86_64" in result.stdout:
            return "x86_64"
        elif "x86" in result.stdout:
            return "x86"
    return None

def install_system_image(sdk_version, arch):
    """Installs the Android system image"""
    print(f"Installing Android {sdk_version} system image ({arch})...")
    run_command(f"sdkmanager \"system-images;android-{sdk_version};google_apis;{arch}\"")

def create_avd(sdk_version, arch):
    """Creates a new AVD"""
    print(f"Creating AVD emulator_{sdk_version}...")
    run_command(f"avdmanager create avd -n emulator_{sdk_version} -k \"system-images;android-{sdk_version};google_apis;{arch}\" -d {DEVICE_TYPE}")
    
    run_command(f"sudo mv /lib/android-sdk/system-images/android-{sdk_version} ~/Android/Sdk/system-images/android-{sdk_version}")

def start_emulator(sdk_version):
    """Starts the Android emulator"""
    print(f"Starting emulator emulator_{sdk_version}...")
    run_command(f"{EMULATOR} -avd emulator_{sdk_version} -no-snapshot-load -no-audio -no-boot-anim -accel on -gpu swiftshader_indirect &")
    
def check_emulator_exists(sdk_version):
    """Checks if the emulator already exists"""
    EMULATOR_NAME = f"emulator_{sdk_version}"
    
    try:
        result = subprocess.run(["avdmanager", "list", "avds"], capture_output=True, text=True, check=True)
        output = result.stdout
        
        # Parse the output to find the emulator
        lines = output.split("\n")
        for i in range(len(lines)):
            if "Name:" in lines[i]:
                avd_name = lines[i].split(": ")[-1].strip()
                if avd_name == EMULATOR_NAME:
                    return True
        
    except subprocess.CalledProcessError as e:
        print("Error executing avdmanager:", e)
    
    return False

def emulator_initialiser(sdk_version):
    """Initializes and starts the emulator"""
    
    # First, check if the emulator already exists
    already_exists = check_emulator_exists(sdk_version)
    if already_exists:
        print(f"Emulator emulator_{sdk_version} already exists.")
        
    already_exists = False
            
    # Check the system image architecture (x86_64 or x86)
    arch = get_system_image_arch(sdk_version)
    if arch is None:
        print(f"System image not found for Android {sdk_version}. Aborting...")
        return
    
    # Proceed with installation, AVD creation, and starting the emulator
    try:
        if not already_exists:
            install_system_image(sdk_version, arch)
            print("\n--------------------\n")
            create_avd(sdk_version, arch)
            print("\n--------------------\n")
        start_emulator(sdk_version)
    except Exception as e:
        print(f"An error occurred: {e}")
        return

# Example usage
emulator_initialiser(36) # versions: 10, 15 - 19, 21 - 36
