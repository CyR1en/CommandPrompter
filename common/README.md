# Common Module
This module contains shared code that's used by CommandPrompter throughout multiple modules.

## CPML - CommandPrompter Markup Language
CPML is a minimal `HTML-like` markup language used for defining prompt and post command elements. It supports structured prompt definitions for chat interfaces, command execution triggers, and more.

#### Syntax Overview

- Root element `<cpml>` can contain multiple child elements.
- Supported child elements inside `<cpml>`:
    - `<prompt>` — defines a prompt with required `id` and `type` attributes, optional `args`.
    - `<postcommand>` — defines a postcommand with required `id` and `type` attributes, optional `args`.
    - `<preconfig/>` — self-closing element with required `id` attribute for pre-configuration settings.
- When inside `<cpml>`, all elements must have an `id`.
- Standalone `<prompt>` or `<postcommand>` elements outside `<cpml>` do not require an `id`.
- Text inside elements can include MiniMessage tags (e.g., `<bold>`) which are ignored by the CPML parser.

#### Example
CPML document will contain pre-configured elements and each element must contain an `id`. 
```html
<cpml>
  <prompt id="gamemode" type="chat">what gamemode do you want?<bold>player</bold>!</prompt>
  <postcommand id="broadcastchange" type="oncomplete">%player_name% changed gamemode to p:0</postcommand>
</cpml>
```
To access any pre-configured elements you can use the following:
```html
<preconfig id="gamemode"/> 
```
You can also define an in-line element
```html
<prompt id="gamemode" type="chat">what gamemode do you want?<bold>player</bold>!</prompt>
```

#### Grammer EBNF
```txt
document         = cpml_element | single_element ;

cpml_element     = "<cpml>" element_list "</cpml>" ;

element_list     = { element } ;

element          = prompt_element | postcommand_element | preconfig_element ;

prompt_element       = "<prompt" required_id prompt_attributes ">" text "</prompt>" ;
postcommand_element  = "<postcommand" required_id postcommand_attributes ">" text "</postcommand>" ;
preconfig_element    = "<preconfig" required_id space? "/>" ;

required_id      = space "id" "=" quote string quote ;

prompt_attributes      = space "type" "=" quote string quote [ space "args" "=" quote string quote ] ;
postcommand_attributes = space "type" "=" quote string quote [ space "args" "=" quote string quote ] ;

single_element   = prompt_single | postcommand_single ;

prompt_single    = "<prompt" prompt_attributes ">" text "</prompt>" ;
postcommand_single = "<postcommand" postcommand_attributes ">" text "</postcommand>" ;

text             = { character | minimessage_tag } ;

minimessage_tag  = "<" string ">" | "</" string ">" ;

character        = ? any character except '<' that does not start a nested tag ? ;

quote            = '"' ;
space            = { ' ' | '\t' | '\r' | '\n' } ;
string           = ? any sequence of valid characters except quote ? ;

```