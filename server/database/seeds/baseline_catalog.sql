INSERT INTO map_template
    (id, name, type, planet, min_zone, max_zone, max_player, data, enabled)
VALUES
    (0, 'Núi Paozu', 'ONLINE', 'EARTH', 10, 100, 15, 1, TRUE),
    (1, 'Bờ sông Pu', 'ONLINE', 'EARTH', 10, 100, 15, 2, TRUE);

INSERT INTO map_waypoint
    (map_id, x, y, type, go_map, go_x, go_y)
VALUES
    (0, 4464, 936, 1, 1, 90, 1008),
    (1, 0, 1008, 0, 0, 4374, 936);

INSERT INTO monster_template
    (id, name, level, hp, damage, potential_reward, range_move, speed, type_move, dart_id,
     icon_move, icon_attack, icon_injure, w, h)
VALUES
    (1, 'Hổ nanh kiếm', 2, 300, 10, 10, 100, 1, 1, 0,
     '[11818,11819,11820,11821,11822]', '[11823]', '[11824]', 175, 95);

INSERT INTO monster_spawn
    (map_id, monster_id, x, y)
VALUES
    (1, 1, 975, 936),
    (1, 1, 1348, 936),
    (1, 1, 1800, 936),
    (1, 1, 2250, 936),
    (1, 1, 2600, 936),
    (1, 1, 2950, 936);
