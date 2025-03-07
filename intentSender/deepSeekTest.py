from openai import OpenAI

# https://openrouter.ai/deepseek/deepseek-r1-distill-llama-70b:free/api

# Read the API key from the file
with open("api.key", "r") as file:
    api_key = file.read().strip()
    
# Check if the API key is empty
    if not api_key:
        raise ValueError("The API key file is empty. Please add your API key to the 'api.key' file.")
    
client = OpenAI(
  base_url="https://openrouter.ai/api/v1",
  api_key=api_key,
)

prompt = "Number from 1 to 100, separated by -:-, only the numbers, no extra text, no explanations, no thoughts, just the output"

completion = client.chat.completions.create(
  model="deepseek/deepseek-r1-distill-llama-70b:free",
  messages=[
    {
      "role": "user",
      "content": prompt
    }
  ]
)
print(completion.choices[0].message.content)