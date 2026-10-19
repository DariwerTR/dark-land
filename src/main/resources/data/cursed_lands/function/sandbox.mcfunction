# Cursed Lands: песочница для проверки построек. Плоское измерение cursed_lands:sandbox, пол на высоте 66 (первый воздух над землёй).
# Раскладка (x, z): часовня 0..52 x 0..59, склеп 70..130 x 0..70, мост 150..226 x 20..34 (настил на y=84), замок 0..132 x 100..246.
# Команда телепортирует вас, подгружает чанки и через 5 секунд ставит все постройки (функция cursed_lands:sandbox_build).
execute in cursed_lands:sandbox run forceload add 0 0 230 250
execute in cursed_lands:sandbox run tp @s 80 66 85 0 0
tellraw @s {"text":"Песочница Cursed Lands: подгружаю чанки, постройки появятся через 5 секунд.","color":"gray"}
schedule function cursed_lands:sandbox_build 100t replace
