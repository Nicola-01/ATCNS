from openai import OpenAI

# Read the API key from the file
with open("api.key", "r") as file:
    api_key = file.read().strip()

# Check if the API key is empty
if not api_key:
    raise ValueError("The API key file is empty. Please add your API key to the 'api.key' file.")

# Initialize the OpenAI-compatible client for Groq
client = OpenAI(
    base_url="https://api.groq.com/openai/v1",
    api_key=api_key,
)

def query_groq(apk_package, intent_command, logs):
    """
    Query Groq with the given APK package and logs.
    
    Args:
        apk_package (str): The APK package name.
        logs (str): The logs to be analyzed.
    
    Returns:
        str: The response from Groq.
    """
    
    prompt = f"We are testing an Android application with package name {apk_package}. \
        Here is an adb intent command or drozer command that was sent to an emulator: \
        Intent command: \
        {intent_command} \
        Here is the output of `adb logcat -d` after sending the intent: \
        {logs} \
        Based on the logs, determine whether the intent caused an error, crash, or was ignored. \
        Recap also the apk package name and the extras (type and value) \
        "
        
        
    response = client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        messages=[
            {"role": "system", "content": "You are an Android developer assistant. \
             You reply only in JSON , with the following format: \
                { \
                    \"apk_package\": \"Package name of the APK\", \
                    \"action\": \"Action of the intent\", \
                    \"extras\": [ \
                        { \
                            \"key\": \"Key of the extra\", \
                            \"type\": \"Type of the extra\", \
                            \"value\": \"Value of the extra\" \
                        } \
                    ], \
                    \"error\": true or false, \
                    \"reason\": \"Short explanation of what happened or might be wrong\" \
                } \
            Return as plaintext, without ```" },
            {"role": "user", "content": prompt}
            ]
    )
    
    return response.choices[0].message.content