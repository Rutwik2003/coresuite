import requests

url = 'https://api.mineskin.org/generate/upload'
files = {'file': open('src/main/resources/skins/ancient_angler.png', 'rb')}
data = {'visibility': 1}
response = requests.post(url, files=files, data=data)
print(response.status_code)
print(response.text)
