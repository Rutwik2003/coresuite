import random
fish_names = [
    "alexpaima", "alpha_spiderfish", "amanita_fungifin", "anchovy", "angelfish", "arapaima", 
    "arch_wyverntail", "ariwana", "armored_cavefish", "armor_trimfish", "arowana", 
    "ash_soul_gaper", "atlantic_cod", "barbel_steed", "barred_knifejaw", "barreleye", 
    "batfish", "betta", "bitterling", "black_bass", "black_minnow", "blowfish", "bluegill", 
    "blue_marlin", "bonefish", "bumblebee_tuna", "butterfly_fish", "capn_tunabeard", "carp", 
    "catfish", "catfishfish", "cat_fish", "chaos_fish", "char", "cheep_cheep", "cherry_salmon", 
    "chickenfish", "chicken_jockeyfish", "cloudfish", "coelacanth", "corrupted_slimefish", 
    "crimson_tigerfish", "crucian_carp", "cursedfish", "cykloppsfish", "dab", "dace", 
    "damselfish", "deathly_fin", "demonic_netherfish", "derpfish", "dirtfish", "dorado", 
    "double_cod", "drowned_king_fish", "eater_of_plankton", "ebonkoi", "eel", "efelounder", 
    "enchanted_bookfish", "enchanted_bookfish_christmas", "enchanted_bookfish_halloween", 
    "fallen_starfish", "filet_o_fish", "fin_shady", "fish", "fishbrine", "fishing_rod_fish", 
    "fishlin", "fishlin_brute", "fishotron", "fishotron_prime", "fishron", "fish_barrel", 
    "flarefin_koi", "flint_and_steelfish", "flying_fish", "football_fish", "freshwater_goby", 
    "frost_minnow", "gar", "giant_snakehead", "giant_trevally", "golden_carp", "golden_trout", 
    "goldfish", "guppy", "harpyfish", "heartfish", "hemopiranha", "honeyfin", "horse_mackerel", 
    "hungerfish", "infected_scabbardfish", "i_am_stevefish", "jack_o_fish", "jake_the_fish", 
    "jewelfish", "kai_carp", "killifish", "king_of_the_pond", "king_salmon", "koi", "lavager", 
    "lava_particle", "laviathan_fry", "loach", "loovar", "mahi_mahi", "makenarel", "malfisha", 
    "mickey_joefish", "mirage_fish", "moaf", "moray_eel", "mudfish", "mutant_flinxfin", 
    "mutated_cod", "mystery_fish", "nameless_fish", "napoleonfish", "necromancer_fish", 
    "neon_tetra", "neptoona", "netherwrasse", "nibbler", "nibble_fish", "noor_tetra", "oarfish", 
    "obsidifish", "ocean_sunfish", "olive_flounder", "pack", "pain_eel", "pale_chub", "pamf", 
    "pengfish", "pike", "piranha", "pixiefish", "plain_ol_fish", "plankton", "pond_smelt", 
    "popeyed_goldfish", "princess_fish", "prismite", "rabbitfish", "rainbowfish", "rainbow_trout", 
    "ranchu_goldfish", "red_snapper", "resinfish", "ribbon_eel", "rusty_marlin", "saddled_bichir", 
    "scarab_fish", "scorpio_fish", "sea_bass", "seven_finned_death", "skippyjack", "slimefish", 
    "small_fry", "soulfish", "specular_fish", "spiderfish", "spiderfish_queen", "stevelapia", 
    "stinkfish", "stowfish", "stringfish", "sturgeon", "suckerfish", "sunnyfish", "surgeonfish", 
    "sweetfish", "testifish", "the_beastfish", "the_fish_of_cthulu", "the_great_hogfish", 
    "the_nibbler", "the_uncatchable", "tilapia", "tnt_fish", "touchy_fish", "tropical_barracuda", 
    "tropical_slimefish", "tuna", "tundra_trout", "unicorn_fish", "variegated_lardfish", 
    "volcanic_bass", "voodoo_dollfin", "wither_bonefish", "wolf_fish", "wyverntail", "yellow_perch", 
    "zebra_turkeyfish", "zishlin", "zombie_fish", "zurigeon"
]
rarities = [
    {"name": "Common", "color": "&7", "chance": 50.0, "price": 10.0},
    {"name": "Uncommon", "color": "&a", "chance": 30.0, "price": 25.0},
    {"name": "Rare", "color": "&9", "chance": 12.0, "price": 100.0},
    {"name": "Epic", "color": "&5", "chance": 6.0, "price": 500.0},
    {"name": "Legendary", "color": "&6", "chance": 2.0, "price": 2000.0}
]
biomes_sets = [
    ["OCEAN", "DEEP_OCEAN", "WARM_OCEAN"],
    ["RIVER", "SWAMP", "MANGROVE_SWAMP"],
    ["FROZEN_OCEAN", "SNOWY_TAIGA", "ICE_SPIKES"],
    ["JUNGLE", "BAMBOO_JUNGLE", "SPARSE_JUNGLE"],
    ["DESERT", "BADLANDS"],
    ["NETHER_WASTES", "CRIMSON_FOREST", "BASALT_DELTAS"]
]
with open('c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/resources/modules/custom-fishing.yml', 'a', encoding='utf-8') as f:
    f.write('\nspecies:\n')
    for fish in fish_names:
        if fish == 'pack': continue
        display_name = ' '.join([w.capitalize() for w in fish.split('_')])
        rarity = random.choices(rarities, weights=[r['chance'] for r in rarities], k=1)[0]
        biomes = []
        if any(x in fish for x in ['nether', 'lava', 'wither', 'soul', 'volcanic', 'demon', 'bone', 'obsidi', 'fire']):
            biomes = ['NETHER_WASTES', 'CRIMSON_FOREST', 'BASALT_DELTAS', 'SOUL_SAND_VALLEY']
        elif any(x in fish for x in ['frost', 'tundra', 'ice', 'snow', 'peng']):
            biomes = ['FROZEN_OCEAN', 'SNOWY_TAIGA', 'ICE_SPIKES', 'FROZEN_RIVER']
        elif any(x in fish for x in ['jungle', 'tropical', 'mahi', 'piranha', 'amazon']):
            biomes = ['JUNGLE', 'BAMBOO_JUNGLE', 'SPARSE_JUNGLE', 'WARM_OCEAN']
        elif any(x in fish for x in ['swamp', 'mud', 'slime', 'zombie', 'catfish']):
            biomes = ['SWAMP', 'MANGROVE_SWAMP', 'RIVER']
        elif rarity['name'] == 'Legendary' and random.random() < 0.3:
            biomes = []
        else:
            biomes = random.choice(biomes_sets)
        chance = rarity['chance'] / 2.0 + random.uniform(0.1, 2.0)
        min_weight = round(random.uniform(0.5, 5.0), 2)
        max_weight = round(min_weight + random.uniform(2.0, 50.0), 2)
        if rarity['name'] in ['Epic', 'Legendary']:
            max_weight *= 2
        f.write(f'  {fish}:\n')
        f.write(f'    name: \"{rarity["color"]}{display_name}\"\n')
        f.write(f'    lore:\n')
        f.write(f'      - \"&7Rarity: {rarity["color"]}{rarity["name"]}\"\n')
        f.write(f'      - \"&7A beautiful fish found in the wild.\"\n')
        f.write(f'    chance: {round(chance, 2)}\n')
        f.write(f'    min-weight: {min_weight}\n')
        f.write(f'    max-weight: {round(max_weight, 2)}\n')
        f.write(f'    price-per-kg: {rarity["price"]}\n')
        f.write(f'    material: COD\n')
        f.write(f'    item-model: barely_default:{fish}\n')
        if biomes:
            f.write(f'    biomes:\n')
            for b in biomes:
                f.write(f'      - {b}\n')
        else:
            f.write(f'    biomes: []\n')
