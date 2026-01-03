# Feature Brainstorming: Emergent Mod

The "Emergent" mod focuses on dynamic, system-driven interactions where the environment and entities react in unpredictable (but logical) ways. Here are several proposed features that align with this philosophy.

---

## 🔥 Fire & Heat Systems

### Campfire & Heat Source Ignition
**Concept**: Open flames spread fire; heat melts ice.
*   **Mechanics**:
    *   Lit campfires ignite adjacent flammable blocks (logs, leaves, wool). Soul campfires exempt.
    *   Lava/fire melt ice and snow within ~3 blocks radius over time.
    *   Fire/lava destroys redstone dust, repeaters, and comparators.
*   **Emergent Result**: Indoor campfires require fireproofing. Ice builds near heat sources are impossible. Redstone circuits can be sabotaged with fire.

### Passive Arrow Ignition
**Concept**: Arrows stuck in blocks catch fire from nearby flames.
*   **Mechanic**: Stuck arrows check for fire/lava proximity; ignited arrows ignite the block they're embedded in.
*   **Emergent Result**: Stray arrows in wooden structures become fire hazards during battles.

---

## 💥 Explosions & Physics

### Enhanced Explosion Effects
**Concept**: Explosions have realistic secondary effects.
*   **Mechanics**:
    *   Glass blocks/panes shatter in a larger radius than normal blast damage would allow.
    *   Unstable blocks (sand/gravel) cascade/fall in a wider radius around blast holes.
    *   TNT Minecarts explode when colliding at high velocity (~8 blocks/sec).
*   **Emergent Result**: Windows are liabilities near combat. Mining explosions cause cave-ins. Derailed TNT minecarts are catastrophic.

### Structural Integrity *(Complex — Config Toggle)*
**Concept**: Unsupported blocks eventually fall.
*   **Mechanic**: Blocks beyond a horizontal distance from support become unstable and fall like sand/gravel after a delay.
*   **Emergent Result**: Realistic building constraints. Floating structures collapse. Mining becomes more dangerous.
*   **Note**: High complexity, should be opt-in via config.

---

## 🌧️ Weather Effects

### Storm Impacts
**Concept**: Weather affects the world dynamically.
*   **Mechanics**:
    *   Heavy rain (thunderstorms) extinguishes exposed torches and campfires.
    *   Lightning arcs between nearby conductive blocks (iron blocks, chains, lightning rods) within ~5 blocks, dealing reduced damage per arc.
*   **Emergent Result**: Light security during storms becomes a priority. Metal structures become lightning magnets.

---

## 🧟 Mob Behavior

### Skeleton Fire Avoidance
**Concept**: Undead fear their weakness.
*   **Mechanic**: Skeletons gain a flee goal to avoid fire blocks and burning entities (lower priority than attack).
*   **Emergent Result**: Fire becomes a defensive tool against skeleton swarms.

### Heavy Mob Trampling
**Concept**: Large creatures destroy fragile obstacles.
*   **Mechanic**: Ravagers, Iron Golems, and Wardens destroy weak blocks (crops, leaves, glass panes, torches, flowers) when walking.
*   **Emergent Result**: Fighting large mobs near your greenhouse is catastrophic.

---

## 🌱 Item & Environmental Reactivity

### Volatile Items ✅ *(Implemented)*
**Status**: Core explosive reactivity is implemented. Dropped TNT/gunpowder/fire charges explode from fire, lava, or explosions.

### Reforestation / Self-Planting
**Concept**: Nature reclaims the world.
*   **Mechanic**: Saplings, seeds, mushrooms, and berries dropped on valid soil auto-plant after ~30 seconds.
*   **Emergent Result**: Deforested areas regrow naturally. Farms "leak" into the wild.

---

## 🏗️ Fragile Structures

### Projectile & Combat Damage
**Concept**: The world takes combat damage.
*   **Mechanic**: Projectiles (arrows, tridents) break glass and glass panes on impact.
*   **Emergent Result**: Windows are liabilities in skeleton shootouts.

---

## ✅ Already Implemented

| Feature | Status |
|---------|--------|
| Volatile Items (dropped explosives) | ✅ Done |
| Reactive Creepers (chain detonation) | ✅ Done |
| Burning Entity Fire Spread | ✅ Done |
| Sculk Shrieker Always Summons | ✅ Done |
