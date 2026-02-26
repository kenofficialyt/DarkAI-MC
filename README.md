# DarkAI MC

AI-powered Minecraft server assistant plugin by **THT**

## Features

### 🤖 Server Fixer
- Analyzes server logs and errors
- Provides AI-powered solutions for server issues
- Helps fix configurations and code problems

### 🔒 Anti-Cheat AI (Advanced)
**Movement Detection:**
- Speed hacking
- Flying
- Teleporting
- Velocity
- Jesus (water walking)
- NoSlowdown

**Combat Detection:**
- Reach (illegal reach)
- KillAura/AutoClicker
- AimAssist
- Rapid fire

**Misc Detection:**
- FastBreak
- FastPlace
- Scaffold
- InventoryMove
- BookBanning

**Punishment System:**
- 1st offense: Kick player
- 3rd offense: Ban IP + Player

### 🛠️ Config Editor
- View any plugin config
- Edit any plugin config
- AI analyze plugin configs
- Backup plugin configs

### 🏗️ Building Assistant
- Generate structures from text descriptions
- AI creates Minecraft block layouts
- Build anything with simple commands

### ⚔️ Item & Trade Creator
- Create custom items with AI
- MMOItems support
- AdvancedEnchantments support
- ItemsAdder support
- Oraxen support
- MythicMobs support
- Design villager trades

### 🔍 Virus Scanner
- Scan plugins for malicious code
- Detect suspicious patterns
- AI-powered threat analysis
- Protect your server from bad plugins

## Supported Versions
- Minecraft 1.17+
- Minecraft 1.18+
- Minecraft 1.19+
- Minecraft 1.20+
- Minecraft 1.21+

## Supported AI Providers
- **OpenAI** (GPT-4)
- **Anthropic** (Claude)
- **Ollama** (Local/Llama2)

## Commands

| Command | Description |
|---------|-------------|
| `/ai help` | Show all commands |
| `/ai fix <issue>` | Analyze and fix server issues |
| `/ai scan <player>` | Scan player for cheating |
| `/ai build <desc>` | Build structure from description |
| `/ai item <spec>` | Create custom item |
| `/ai trade <spec>` | Create villager trade |
| `/ai mmoitem <spec>` | Create MMOItems item |
| `/ai enchant <spec>` | Create enchantment |
| `/ai itemsadder <spec>` | Create ItemsAdder item |
| `/ai oraxen <spec>` | Create Oraxen item |
| `/ai mythicmobs <spec>` | Create MythicMobs item |
| `/ai config view <plugin>` | View plugin config |
| `/ai config edit <plugin> <key> <value>` | Edit config |
| `/ai config analyze <plugin>` | AI analyze config |
| `/ai config backup <plugin>` | Backup config |
| `/ai scan-plugins` | Scan plugins for threats |
| `/ai reload` | Reload configuration |

## Permissions

```yaml
ai.admin        - Full access (default: op)
ai.fix         - Use /ai fix
ai.scan        - Use /ai scan  
ai.build       - Use /ai build
ai.item        - Use /ai item, mmoitem, enchant, etc.
ai.trade       - Use /ai trade
ai.scanplugins - Use /ai scan-plugins
ai.reload      - Use /ai reload
```

## Installation

1. Download the plugin JAR
2. Place in `plugins/` folder
3. Edit `config.yml` with your API key
4. Restart server

## Configuration

Edit `config.yml` to configure:

```yaml
ai:
  provider: "openai"          # openai, anthropic, ollama
  openai-key: "YOUR_KEY"     # Your API key
  model: "gpt-4"            # AI model

features:
  server-fixer: true
  anti-cheat: true
  building: true
  item-creator: true
  virus-scanner: true
  config-editor: true

anticheat:
  enabled: true
  alert-threshold: 3
  ban-threshold: 3
  
  detection:
    movement: true
    combat: true
    misc: true
    advanced: true

language: "en"              # en or vi
```

## Languages
- English (en)
- Vietnamese (vi)

## License
GPL-3.0 License

## Author
**THT**
