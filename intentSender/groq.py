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

response = client.chat.completions.create(
    model="llama-3.3-70b-versatile",  # or llama2-70b-4096
    messages=[
        {"role": "system", "content": "You are a helpful assistant. You reply only in JSON."},
        {"role": "user", "content": "Explain Groq in one sentence."}
    ]
)

print(response.choices[0].message.content)
