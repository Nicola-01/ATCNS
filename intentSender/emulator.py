import re
import subprocess
import os
import time

# Set your Android SDK root and emulator path
ANDROID_SDK_ROOT = "~/Android/Sdk"
EMULATOR = f"~/Android/Sdk/emulator/emulator"

def run_command(command, wait_end=True, print_output=True):
    """
    Executes a shell command with optional real-time printing and wait behavior.

    Args:
        command (str): The shell command to execute.
        wait_end (bool): Whether to wait for the command to finish.
        print_output (bool): Whether to print the stdout/stderr live.
    """
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    if print_output:
        for line in iter(process.stdout.readline, ''):
            print(line, end='')  # Output stdout in real time
        for line in iter(process.stderr.readline, ''):
            print(line, end='')  # Output stderr in real time

        process.stdout.close()
        process.stderr.close()
    
    if wait_end:
        process.wait()

def get_available_sdk_images():
    """
    Retrieves all available Android system images with google_apis support.

    Returns:
        dict: Mapping of sdk_version -> image identifier string.
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
            if sdk_version not in sdk_map or arch == "x86_64":
                sdk_map[sdk_version] = sdk_key  # Prefer x86_64
    return sdk_map

def get_system_image_arch(sdk_version):
    """
    Detect the architecture (x86_64 or x86) of a system image.

    Args:
        sdk_version (int): The Android SDK version.

    Returns:
        str or None: Architecture string if found, else None.
    """
    result = subprocess.run(
        f"sdkmanager --list | grep 'system-images;android-{sdk_version}' | grep 'google_apis'",
        shell=True, capture_output=True, text=True
    )
    if result.returncode == 0:
        if "x86_64" in result.stdout:
            return "x86_64"
        elif "x86" in result.stdout:
            return "x86"
    return None

def install_system_image(sdk_image):
    """
    Install a system image via sdkmanager.

    Args:
        sdk_image (str): The image string (e.g., system-images;android-33;google_apis;x86_64).
    """
    print(f"Installing Android {sdk_image} system image...")
    run_command(f"sudo sdkmanager \"{sdk_image}\"")

def create_avd(sdk_version, sdk_image):
    """
    Create an Android Virtual Device for the specified system image.

    Args:
        sdk_version (int): Android SDK version.
        sdk_image (str): The image identifier.
    """
    print(f"Creating AVD emulator_{sdk_version}...")
    run_command(f"avdmanager create avd -n emulator_{sdk_version} -k \"{sdk_image}\" -d pixel")
    
    USER_HOME = os.path.expanduser("~")
    print(f"Moving AVD to {USER_HOME}/Android/Sdk/system-images/android-{sdk_version}...")
    run_command(f"sudo mv /lib/android-sdk/system-images/android-{sdk_version} $HOME/Android/Sdk/system-images/android-{sdk_version}")

def start_emulator(sdk_version):
    """
    Launch the emulator for a given SDK version.

    Args:
        sdk_version (int): The version used in the AVD name.
    """
    print(f"Starting emulator emulator_{sdk_version}...")
    run_command(
        f"{EMULATOR} -avd emulator_{sdk_version} "
        "-no-snapshot-load -no-audio -no-boot-anim -accel on -gpu swiftshader_indirect &",
        wait_end=False,
        print_output=False
    )

def check_emulator_exists(sdk_version):
    """
    Check if an AVD already exists.

    Args:
        sdk_version (int): SDK version used in the AVD name.

    Returns:
        bool: True if AVD exists, False otherwise.
    """
    EMULATOR_NAME = f"emulator_{sdk_version}"
    try:
        emulator_path = os.path.expanduser("~/Android/Sdk/emulator/emulator")
        result = subprocess.run([emulator_path, "-list-avds"], capture_output=True, text=True, check=True)
        return EMULATOR_NAME in result.stdout.splitlines()
    except subprocess.CalledProcessError as e:
        print("Error executing avdmanager:", e)
    return False

def is_emulator_online():
    """
    Check if an emulator is running and connected.

    Returns:
        bool: True if any emulator is online, False otherwise.
    """
    try:
        result = subprocess.run("adb devices", shell=True, capture_output=True, text=True, check=True)
        lines = result.stdout.strip().split("\n")
        for line in lines[1:]:
            if line.startswith("emulator-") and "\tdevice" in line:
                return True
    except subprocess.CalledProcessError as e:
        print("Failed to check ADB devices:", e)
    return False

def closeAllEmulators():
    """
    Kill all currently running emulator instances.
    """
    try:
        subprocess.run(
            "adb devices | grep emulator | cut -f1 | while read line; do adb -s $line emu kill; done",
            shell=True, capture_output=True, text=True, check=True
        )
        time.sleep(5)
    except subprocess.CalledProcessError as e:
        print("Failed to close emulators:", e)

def unlock_emulator():
    """
    Send key events to unlock the emulator screen.
    """
    print("Unlocking emulator screen...")
    subprocess.run("adb shell input keyevent 224", shell=True)  # WAKE
    time.sleep(1)
    subprocess.run("adb shell input keyevent 82", shell=True)   # MENU/UNLOCK

def emulator_initialiser(sdk_version):
    """
    Ensure an emulator exists, create it if needed, and boot it.
    If the requested SDK version is not available, fall forward to next supported one.

    Args:
        sdk_version (int): Requested SDK version.

    Returns:
        str or None: Emulator name if launched, None if failed.
    """
    closeAllEmulators()  # Kill all existing emulators

    sdk_map = get_available_sdk_images()
    if not sdk_map:
        print("No SDK images available.")
        return None

    max_sdk_version = max(sdk_map.keys())
    while sdk_version not in sdk_map:
        print(f"System image not found for Android SDK {sdk_version}. Trying next version...")
        sdk_version += 1
        if sdk_version > max_sdk_version:
            print(f"No available system image found for Android SDK {sdk_version}. Aborting...")
            return None

    already_exists = check_emulator_exists(sdk_version)
    if already_exists:
        print(f"Emulator emulator_{sdk_version} already exists.")

    if not already_exists:
        install_system_image(sdk_map[sdk_version])
        print("\n--------------------\n")
        create_avd(sdk_version, sdk_map[sdk_version])
        print("\n--------------------\n")

    start_emulator(sdk_version)

    for _ in range(30):  # Wait up to 90 seconds
        if is_emulator_online():
            print("Emulator is online.")
            unlock_emulator()
            break
        time.sleep(3)
    else:
        print("Emulator did not come online in time.")
        return None

    return f"emulator_{sdk_version}"