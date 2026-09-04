package com.fakerevive.disguise;

/**
 * Shared word lists used to build Minecraft username candidates. Kept in one place so the
 * runtime {@link MojangIdentityFetcher} and the offline {@code NameListGenerator} dev tool draw
 * from the same vocabulary - the fetcher previously carried its own 30x30 lists, which was far
 * too small a space to keep finding unclaimed names.
 */
public final class NameVocabulary {

    public static final String[] ADJECTIVES = {
            "Silent", "Shadow", "Crimson", "Frozen", "Golden", "Swift", "Dark", "Ancient", "Wild", "Blazing",
            "Rusty", "Mystic", "Savage", "Lucky", "Grim", "Bold", "Quiet", "Sneaky", "Feral", "Toxic",
            "Neon", "Cosmic", "Rogue", "Vivid", "Brave", "Cursed", "Wicked", "Noble", "Iron", "Stormy",
            "Rapid", "Bitter", "Lone", "Sly", "Fierce", "Icy", "Molten", "Ghostly", "Deadly", "Windy",
            "Happy", "Lazy", "Crazy", "Epic", "Super", "Turbo", "Retro", "Cool", "Chill", "Angry",
            "Jolly", "Wacky", "Funky", "Groovy", "Spicy", "Salty", "Sweet", "Sour", "Bright", "Dim",
            "Sharp", "Dull", "Heavy", "Quick", "Slow", "Loud", "Soft", "Hard", "Smooth", "Rough",
            "Tiny", "Giant", "Mini", "Wide", "Thin", "Slim", "Round", "Square", "Curly", "Spiky",
            "Fuzzy", "Shiny", "Glowing", "Sparkly", "Misty", "Foggy", "Sunny", "Rainy", "Snowy", "Cloudy",
            "Electric", "Magnetic", "Atomic", "Solar", "Lunar", "Astral", "Royal", "Regal", "Divine", "Sacred",
            "Rebel", "Hidden", "Secret", "Masked", "Phantom", "Spectral", "Eternal", "Infinite", "Endless", "Timeless",
            "Chaotic", "Wandering", "Roaming", "Drifting", "Hunting", "Prowling", "Stalking", "Lurking", "Vanishing", "Fading",
            "Burning", "Freezing", "Melting", "Shining", "Gleaming", "Radiant", "Dazzling", "Brilliant", "Vibrant", "Steel",
            "Copper", "Silver", "Bronze", "Platinum", "Diamond", "Ruby", "Emerald", "Sapphire", "Obsidian", "Amber"
    };

    public static final String[] NOUNS = {
            "Wolf", "Falcon", "Ninja", "Knight", "Ghost", "Raven", "Tiger", "Dragon", "Pirate", "Ranger",
            "Hunter", "Viper", "Panther", "Phoenix", "Golem", "Wizard", "Archer", "Bandit", "Reaper", "Goblin",
            "Yeti", "Sniper", "Cobra", "Badger", "Otter", "Falconer", "Rider", "Sailor", "Miner", "Smith",
            "Nomad", "Warden", "Scout", "Hawk", "Lynx", "Puma", "Drifter", "Marauder", "Sentinel", "Gamer",
            "Player", "King", "Queen", "Legend", "Boss", "Star", "Panda", "Fox", "Bear", "Shark",
            "Eagle", "Owl", "Bat", "Snake", "Spider", "Scorpion", "Mantis", "Beetle", "Wasp", "Hornet",
            "Lion", "Leopard", "Cheetah", "Jaguar", "Bison", "Buffalo", "Elk", "Moose", "Deer", "Rabbit",
            "Squirrel", "Raccoon", "Skunk", "Weasel", "Ferret", "Mole", "Hedgehog", "Porcupine", "Beaver", "Chipmunk",
            "Crow", "Sparrow", "Robin", "Cardinal", "Woodpecker", "Pelican", "Heron", "Stork", "Swan", "Duck",
            "Goose", "Turkey", "Rooster", "Peacock", "Parrot", "Toucan", "Flamingo", "Penguin", "Puffin", "Albatross",
            "Whale", "Dolphin", "Orca", "Narwhal", "Squid", "Octopus", "Jellyfish", "Crab", "Lobster", "Turtle",
            "Frog", "Toad", "Newt", "Salamander", "Chameleon", "Iguana", "Gecko", "Lizard", "Crocodile", "Alligator",
            "Zombie", "Skeleton", "Vampire", "Werewolf", "Demon", "Angel", "Titan", "Colossus", "Behemoth", "Kraken",
            "Hydra", "Griffin", "Chimera", "Basilisk", "Wyvern", "Serpent", "Mummy", "Specter", "Wraith", "Banshee",
            "Samurai", "Shogun", "Warrior", "Gladiator", "Berserker", "Paladin", "Templar", "Crusader", "Monk", "Sage",
            "Oracle", "Prophet", "Seer", "Alchemist", "Sorcerer", "Enchanter", "Druid", "Shaman", "Priest", "Captain",
            "General", "Commander", "Admiral", "Chief", "Marshal", "Colonel", "Major", "Sergeant", "Private", "Rocket",
            "Comet", "Meteor", "Nova", "Nebula", "Galaxy", "Cosmos", "Void", "Storm", "Blaze", "Frost",
            "Ember", "Flame", "Spark", "Bolt", "Thunder", "Quake", "Tremor", "Avalanche", "Cyclone", "Tornado",
            "Hurricane", "Blizzard", "Monsoon", "Tsunami", "Vortex", "Eclipse", "Zenith", "Apex", "Summit", "Peak"
    };

    public static final String[] SINGLE_WORDS = {
            "Steve", "Alex", "Notch", "Pixel", "Nova", "Zero", "Echo", "Comet", "Nebula", "Cosmo",
            "Orbit", "Lunar", "Solar", "Astro", "Turbo", "Nitro", "Cyber", "Byte", "Chip", "Glitch",
            "Rex", "Max", "Leo", "Jax", "Kai", "Finn", "Zane", "Cole", "Ryder", "Axel",
            "Milo", "Otto", "Hugo", "Theo", "Remy", "Beau", "Jett", "Ace", "Duke", "Earl",
            "Baron", "Knox", "Flynn", "Blitz", "Dash", "Zippy", "Nimbus", "Cirrus", "Cumulus", "Static",
            "Voltage", "Circuit", "Vector", "Matrix", "Cipher", "Cortex", "Nexus", "Pulse", "Signal", "Beacon",
            "Horizon", "Summit", "Ridge", "Canyon", "Mesa", "Delta", "Sigma", "Omega", "Alpha", "Gamma",
            "Kappa", "Onyx", "Jade", "Topaz", "Pearl", "Ivory", "Ebony", "Slate", "Marble", "Granite",
            "Cobalt", "Indigo", "Violet", "Azure", "Coral", "Willow", "Cedar", "Maple", "Birch", "Aspen",
            "Rowan", "Fern", "Clover", "Meadow", "Brook", "River", "Lake", "Ocean", "Reef", "Dune"
    };

    public static final String[] PREFIXES = {
            "The", "Mr", "Mrs", "Its", "Real", "Not", "Lord", "Sir", "Captain", "King", "Queen", "Xx", "Yo", "Big"
    };

    public static final String[] SUFFIXES = {
            "YT", "TV", "Gaming", "Gamer", "Plays", "Pro", "HD", "OP", "X", "Z", "Xx", "Boy", "Girl", "Kid"
    };

    private NameVocabulary() {
    }
}
