import json
import urllib.request
import ssl

def export_emojis(guild_id, bot_token, output_file='src/main/resources/assets/avoutils/custom_emojis.json'):
    url = f"https://discord.com/api/v10/guilds/{guild_id}/emojis"
    req = urllib.request.Request(url)
    req.add_header('Authorization', f'Bot {bot_token}')
    req.add_header('User-Agent', 'DiscordEmojiExporter (1.0)')

    context = ssl._create_unverified_context()

    try:
        print("Fetching emojis from Discord API...")
        with urllib.request.urlopen(req, context=context) as response:
            if response.status == 200:
                data = json.loads(response.read().decode())
                emojis_map = {}
                for emoji in data:
                    emoji_id = emoji['id']
                    emoji_name = emoji['name']
                    emoji_url = f"https://cdn.discordapp.com/emojis/{emoji_id}.png?size=32"
                    emojis_map[emoji_name] = emoji_url
                
                with open(output_file, 'w', encoding='utf-8') as f:
                    json.dump(emojis_map, f, indent=2)
                
                print(f"Successfully exported {len(emojis_map)} emojis to {output_file}!")
            else:
                print(f"Failed to fetch emojis. HTTP status code: {response.status}")
    except Exception as e:
        print(f"Error fetching emojis: {e}")

if __name__ == '__main__':
    guild = input("Enter Discord Server ID: ").strip()
    token = input("Enter Discord Bot Token: ").strip()
    if not guild or not token:
        print("Guild ID and Bot Token are required.")
    else:
        export_emojis(guild, token)
