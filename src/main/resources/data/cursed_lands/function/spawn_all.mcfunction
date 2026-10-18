# Cursed Lands: ставит часовню, склеп и замок одним вызовом (сырые шаблоны, без порчи камня).
# Встаньте на ровной земле в измерении cursed_lands:cursed_lands, на уровне пола (первый воздух над землёй).
# Часовня и склеп уходят на восток (+x), замок на запад (-x). Каждый шаблон растёт от точки в сторону +x и +z.
# Мост здесь не ставится: ему нужен каньон, используйте /function cursed_lands:spawn_bridge в биоме blood_canyon.
place template cursed_lands:ruined_chapel ~10 ~-3 ~
place template cursed_lands:necropolis_crypt ~50 ~-10 ~
place template cursed_lands:dark_castle ~-110 ~-10 ~
