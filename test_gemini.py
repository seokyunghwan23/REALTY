import requests
import json

# Gemini REST API 테스트
API_KEY = "AIzaSyDMQOc8wR8K9-cX6z1kUZAdFahpT-JLDEo"
MODEL = "gemini-2.0-flash-exp"

url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={API_KEY}"

payload = {
    "contents": [{
        "parts": [{
            "text": "안녕하세요. 간단히 인사해주세요."
        }]
    }],
    "generationConfig": {
        "temperature": 0.7,
        "maxOutputTokens": 100
    }
}

print(f"요청 URL: {url}")
print(f"요청 Body: {json.dumps(payload, indent=2, ensure_ascii=False)}")

response = requests.post(url, json=payload)

print(f"\n응답 상태코드: {response.status_code}")
print(f"응답 내용:\n{json.dumps(response.json(), indent=2, ensure_ascii=False)}")
