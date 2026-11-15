# RoyalGuard

Player-model bodyguard bots with customisable PvP. One jar, no other plugins required.
Targets Paper 26.2 (Java 25).

## Get the jar without installing anything (GitHub)
1. Create a free GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, pick **Build RoyalGuard jar**, and run it (it also runs on every push).
3. When it finishes, download the **RoyalGuard-jar** artifact. It contains `RoyalGuard-1.0.0.jar`.
4. Put that jar in your server's `plugins/` folder and restart.

## Or build locally
Install JDK 25 and Gradle, then run `gradle build`. The jar is `build/libs/RoyalGuard-1.0.0.jar`.

## Using it
Tap or right-click your guard to open a menu (works on Bedrock). Or use commands:

    /guard spawn [name] [java-skin-name]
    /guard follow | stay | guard [radius] | bring
    /guard preset casual|normal|sweaty|passive
    /guard set <damage|reach|cooldown|reaction|accuracy|detect|speed|follow|teleport|mobs|players|assist> <value>
    /guard inv                 (edit armour, hands and backpack)
    /guard skin <name> | rename <name> | info | list | select <name> | remove | reload

## Guard inventory
Open it from the guard's menu (Gear and backpack) or with `/guard inv`. It is a normal chest menu:
- Slots 2-5: helmet, chestplate, leggings, boots. Slots 7-8: main hand, off hand.
- Bottom row: a 9-slot backpack. Edible items in it are eaten automatically when the guard drops below 60% health.
- Armour and weapons are real, so they change how much damage the guard takes and deals.
- Items are saved with the guard. If it dies it keeps them; if you dismiss it, everything is returned to you.

## Bedrock
Bedrock players are detected by their Floodgate-style UUID (no Floodgate install needed).
By default Java players see a skinned Mannequin and Bedrock players see the guard's mob body
(`appearance.bedrock: mob` in config.yml). Test a Bedrock client, then try `mannequin`.

## Not verified
This was written without being able to compile or run it against Paper 26.2.
Expect to send back any build or runtime errors.
