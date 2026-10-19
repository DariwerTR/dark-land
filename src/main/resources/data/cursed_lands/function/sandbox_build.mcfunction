# Cursed Lands: ставит все четыре постройки в песочнице по фиксированным координатам (запускается из sandbox, можно и вручную).
execute in cursed_lands:sandbox run place template cursed_lands:ruined_chapel 0 63 0
execute in cursed_lands:sandbox run place template cursed_lands:necropolis_crypt 70 56 0
execute in cursed_lands:sandbox run place template cursed_lands:canyon_bridge 150 36 20
execute in cursed_lands:sandbox run place template cursed_lands:dark_castle 0 56 100
execute in cursed_lands:sandbox run forceload remove 0 0 230 250
tellraw @a {"text":"Песочница готова: часовня, склеп, мост и замок. Замок к югу от вас, склеп и часовня к северу, мост к востоку.","color":"green"}
