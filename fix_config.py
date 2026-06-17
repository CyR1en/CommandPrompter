with open('prompt-paper/src/main/java/dev/cyr1en/promptpaper/config/CommandPrompterConfig.java', 'r') as f:
    text = f.read()

import re
text = re.sub(r'\s*@ConfigNode\s*@NodeName\("Locale"\).*?String locale\n', '', text, flags=re.DOTALL)
with open('prompt-paper/src/main/java/dev/cyr1en/promptpaper/config/CommandPrompterConfig.java', 'w') as f:
    f.write(text)
