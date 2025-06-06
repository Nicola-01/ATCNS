from openai import OpenAI

# Read the API key from the file
with open("api.key", "r") as file:
    api_key = file.read().strip()

# Raise an error if the key is missing
if not api_key:
    raise ValueError("The API key file is empty. Please add your API key to the 'api.key' file.")

# Initialize OpenAI-compatible client (Groq endpoint)
client = OpenAI(
    base_url="https://api.groq.com/openai/v1",
    api_key=api_key,
)

model="llama-3.3-70b-versatile"

def query_groq(apk_package, intent_command, logs):
    """
    Query the Groq LLM with a structured prompt including intent command and adb log output.
    
    Args:
        apk_package (str): Package name of the APK under test.
        intent_command (str): The ADB or Drozer intent command that was executed.
        logs (str): Logcat output after sending the intent.
    
    Returns:
        str: JSON string returned by the LLM summarizing the result and any detected error.
    """
    
    prompt = f"We are testing an Android application with package name {apk_package}. \
        Here is an adb intent command or drozer command that was sent to an emulator:\
        Intent command: \
        {intent_command} \
        \
        Here is the output of `adb logcat -d` after sending the intent:\
        {logs} \
        \
        Based on the logs, determine whether the intent caused an error, crash, or was ignored. \
        Recap also the apk package name and the extras (type and value). \
        "

    # Send the prompt to Groq's LLM, requesting a JSON-only structured response
    response = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "You are an Android developer assistant. "
                    "You reply only in JSON, with the following format:\n"
                    "{\n"
                    "  \"apk_package\": \"Package name of the APK\",\n"
                    "  \"action\": \"Action of the intent\",\n"
                    "  \"extras\": [\n"
                    "    {\"key\": \"Extra name\", \"type\": \"Type\", \"value\": \"Value\" }\n"
                    "  ],\n"
                    "  \"error\": true or false,\n"
                    "  \"reason\": \"Short explanation of what happened or might be wrong\"\n"
                    "}\n"
                    "Return as plaintext only, no markdown or ```"
                )
            },
            {"role": "user", "content": prompt}
        ]
    )

    return response.choices[0].message.content
